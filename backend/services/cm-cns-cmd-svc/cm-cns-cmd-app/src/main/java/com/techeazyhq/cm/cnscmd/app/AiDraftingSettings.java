/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

package com.techeazyhq.cm.cnscmd.app;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfig;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfigRepoPort;
import com.techeazyhq.cm.cnscmd.dom.AiProvider;
import com.techeazyhq.cm.cnscmd.dom.DraftingModelFactoryPort;
import com.techeazyhq.cm.cnscmd.dom.DraftingModelPort;
import com.techeazyhq.cm.cnscmd.dom.SecretSealerPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Which model drafts purposes, chosen by platform staff in CM Admin (ADR-113). It is the {@link DraftingModelPort} the drafting assistant uses
 * (ADR-111), and it swaps the client underneath when the setting changes, here at once and on other instances within a minute (see
 * {@link #reload()}). Until staff first save a setting, the {@code cm.cnscmd.ai-drafting.*} properties apply.
 *
 * <p>The API key is write-only: it is sealed before it is stored, never returned, never logged and never put in the audit trail. A stored key
 * is carried over to a changed setting only when the provider and address are the same, so changing provider can never send one vendor's key
 * to another.
 */
public class AiDraftingSettings implements DraftingModelPort {

    /** What staff send. {@code apiKey} blank keeps the stored key (when the provider and address are unchanged); {@code clearKey} removes it. */
    public record Update(boolean enabled, AiProvider provider, String endpoint, String model, String apiKey, boolean clearKey, Integer timeoutSeconds,
                         Integer maxTokens, boolean thirdPartyAck) {
    }

    /** What staff see: the setting without the key, and what each provider needs. */
    public record View(AiDraftingConfig config, boolean fromProperties, List<ProviderInfo> providers) {
    }

    public record ProviderInfo(AiProvider id, String label, String defaultEndpoint, String defaultModel, boolean endpointEditable, boolean keyRequired,
                               boolean thirdParty) {
    }

    public record TestResult(boolean ok, long millis, String message) {
    }

    private record State(AiDraftingConfig config, boolean fromProperties, DraftingModelPort model) {
    }

    static final String PING_SYSTEM = "You are a connection test. Reply with one JSON object, {\"ok\": true}, and nothing else.";
    static final String PING_USER = "ping";

    private final AiDraftingConfigRepoPort repo;
    private final DraftingModelFactoryPort factory;
    private final SecretSealerPort sealer;
    private final AuditTrail audit;
    private final Clock clock;
    private final AiDraftingConfig fallback;
    private final String fallbackKey;
    private final AtomicReference<State> state;

    /** {@code fallback} and {@code fallbackKey} are what the properties say, used until staff save a setting. */
    public AiDraftingSettings(AiDraftingConfigRepoPort repo, DraftingModelFactoryPort factory, SecretSealerPort sealer, AuditTrail audit, Clock clock,
                              AiDraftingConfig fallback, String fallbackKey) {
        this.repo = repo;
        this.factory = factory;
        this.sealer = sealer;
        this.audit = audit;
        this.clock = clock;
        this.fallback = fallback;
        this.fallbackKey = fallbackKey;
        this.state = new AtomicReference<>(new State(fallback, true, factory.build(fallback, fallbackKey)));
    }

    // ---- the model the assistant uses

    @Override
    public Status status() {
        return state.get().model().status();
    }

    @Override
    public Mono<String> complete(String system, String user) {
        return state.get().model().complete(system, user);
    }

    /**
     * Reads the saved setting and swaps the client when it differs from the one in use. Run when the service starts and every so often, so a change
     * made on one instance reaches the others. A failed read keeps the client in use.
     */
    public Mono<Void> reload() {
        return repo.find()
                .map(saved -> new Loaded(saved, false))
                .defaultIfEmpty(new Loaded(fallback, true))
                .doOnNext(this::install)
                .then()
                .onErrorResume(e -> Mono.empty());
    }

    private record Loaded(AiDraftingConfig config, boolean fromProperties) {
    }

    private void install(Loaded loaded) {
        State current = state.get();
        if (current.config().equals(loaded.config()) && current.fromProperties() == loaded.fromProperties()) {
            return;
        }
        String key = loaded.fromProperties() ? fallbackKey : open(loaded.config());
        state.set(new State(loaded.config(), loaded.fromProperties(), factory.build(loaded.config(), key)));
    }

    // ---- what staff see and do

    public View view() {
        State s = state.get();
        return new View(s.config(), s.fromProperties(), providers());
    }

    public static List<ProviderInfo> providers() {
        return Arrays.stream(AiProvider.values()).map(p -> new ProviderInfo(p, p.label(), p.defaultEndpoint(), p.defaultModel(), p.endpointEditable(),
                p.keyRequired(), !p.endpointEditable())).toList();
    }

    /** Saves a setting and uses it from now on. Audited: who, which provider and model, whether the key changed; never the key. */
    public Mono<View> update(String by, Update u) {
        AiDraftingConfig current = state.get().fromProperties() ? null : state.get().config();
        AiDraftingConfig next;
        try {
            next = candidate(by, u, current).validated();
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-ai-config", e.getMessage()));
        }
        boolean keyChanged = u.clearKey() || (u.apiKey() != null && !u.apiKey().isBlank());
        return repo.save(next)
                .doOnNext(saved -> state.set(new State(saved, false, factory.build(saved, open(saved)))))
                .then(audit.record("platform.ai-drafting-config", null, next.provider().name(), AuditTrail.details("by", by,
                        "enabled", String.valueOf(next.enabled()), "model", next.model(), "keyChanged", String.valueOf(keyChanged),
                        "leavesPlatform", String.valueOf(next.leavesPlatform()))))
                .then(Mono.fromSupplier(this::view)); // after the save, so it shows what was saved
    }

    /**
     * Calls the model with a one-line ping, using the setting as submitted (and the stored key when the provider and address are unchanged), so staff
     * can check it before saving. No fiduciary text is sent. The answer says whether it worked and how long it took, never what the provider said.
     */
    public Mono<TestResult> test(String by, Update u) {
        AiDraftingConfig current = state.get().fromProperties() ? null : state.get().config();
        AiDraftingConfig candidate;
        String key;
        try {
            AiDraftingConfig c = candidate(by, new Update(true, u.provider(), u.endpoint(), u.model(), u.apiKey(), u.clearKey(), u.timeoutSeconds(),
                    u.maxTokens(), true), current);
            candidate = c.validated();
            key = u.apiKey() != null && !u.apiKey().isBlank() ? u.apiKey() : open(c);
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-ai-config", e.getMessage()));
        }
        long start = clock.millis();
        DraftingModelPort model = factory.build(candidate, key);
        return model.complete(PING_SYSTEM, PING_USER)
                .timeout(Duration.ofSeconds(candidate.timeoutSeconds()))
                .map(reply -> reply != null && !reply.isBlank()
                        ? new TestResult(true, clock.millis() - start, "The model answered.")
                        : new TestResult(false, clock.millis() - start, "The model answered with nothing."))
                .onErrorResume(e -> Mono.just(new TestResult(false, clock.millis() - start,
                        "The model could not be reached, was too slow, or refused the key or the model name.")))
                .flatMap(r -> audit.record("platform.ai-drafting-test", null, candidate.provider().name(), AuditTrail.details("by", by,
                        "model", candidate.model(), "outcome", r.ok() ? "ok" : "failed")).onErrorResume(x -> Mono.empty()).thenReturn(r));
    }

    // ---- building a setting from what was submitted

    /** The submitted setting as a record, its key sealed. A stored key stays only while the provider and the address are the same. */
    private AiDraftingConfig candidate(String by, Update u, AiDraftingConfig current) {
        if (u.provider() == null) {
            throw new IllegalArgumentException("Choose a provider");
        }
        AiProvider provider = u.provider();
        String endpoint = provider.endpointEditable() ? (u.endpoint() == null ? "" : u.endpoint().strip()) : provider.defaultEndpoint();
        String sealed = null;
        if (u.apiKey() != null && !u.apiKey().isBlank()) {
            sealed = sealer.seal(u.apiKey().strip());
        } else if (!u.clearKey() && current != null && current.provider() == provider && sameAddress(current.endpoint(), endpoint)) {
            sealed = current.apiKeySealed();
        }
        return new AiDraftingConfig(u.enabled(), provider, endpoint, u.model() == null ? "" : u.model().strip(), sealed,
                u.timeoutSeconds() == null ? 100 : u.timeoutSeconds(), u.maxTokens() == null ? 1500 : u.maxTokens(), u.thirdPartyAck(), by, clock.instant());
    }

    private static boolean sameAddress(String a, String b) {
        return trim(a).equalsIgnoreCase(trim(b));
    }

    private static String trim(String s) {
        String t = s == null ? "" : s.strip();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    private String open(AiDraftingConfig c) {
        return c.hasKey() ? sealer.open(c.apiKeySealed()) : null;
    }
}
