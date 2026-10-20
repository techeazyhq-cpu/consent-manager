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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.dom.SecretSealerPort;
import com.techeazyhq.cm.cnscmd.dom.WhkDelivery;
import com.techeazyhq.cm.cnscmd.dom.WhkEndpoint;
import com.techeazyhq.cm.cnscmd.dom.WhkMessage;
import com.techeazyhq.cm.cnscmd.dom.WhkRepoPort;
import com.techeazyhq.cm.cnscmd.dom.WhkSenderPort;
import com.techeazyhq.cm.cnscmd.dom.WhkSignature;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A fiduciary's webhooks (ADR-047): its endpoints, their signing secrets, and the deliveries made to them. The secret
 * is shown once, when made or rotated, and kept sealed. Every change is recorded with who made it (ADR-040).
 */
public class Webhooks {

    private final WhkRepoPort repo;
    private final WhkSenderPort sender;
    private final SecretSealerPort sealer;
    private final AuditTrail audit;
    private final Clock clock;
    private final boolean allowHttp;

    public Webhooks(WhkRepoPort repo, WhkSenderPort sender, SecretSealerPort sealer, AuditTrail audit, Clock clock,
                    boolean allowHttp) {
        this.repo = repo;
        this.sender = sender;
        this.sealer = sealer;
        this.audit = audit;
        this.clock = clock;
        this.allowHttp = allowHttp;
    }

    /** An endpoint, and its signing secret in plain: the only time it is shown. */
    public record WithSecret(WhkEndpoint endpoint, String secret) {
    }

    public Flux<WhkEndpoint> endpoints(String dfId) {
        return repo.endpoints(dfId);
    }

    public Mono<WhkEndpoint> endpoint(String dfId, String id) {
        return repo.findEndpoint(dfId, id)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "webhook-not-found", "No webhook endpoint " + id)));
    }

    @Transactional
    public Mono<WithSecret> create(String dfId, String url, String description, Set<String> types) {
        try {
            WhkEndpoint.check(url, description, types, allowHttp);
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-webhook", e.getMessage()));
        }
        String cleanUrl = url.strip();
        return sender.refusal(cleanUrl)
                .flatMap(refused -> refused.isPresent()
                        ? Mono.<Long>error(new DomRuleViolation(400, "invalid-webhook", refused.get()))
                        : repo.endpoints(dfId).count())
                .flatMap(count -> {
                    if (count >= WhkEndpoint.MAX_PER_DF) {
                        return Mono.error(new DomRuleViolation(409, "too-many-webhooks",
                                "An organisation can have at most " + WhkEndpoint.MAX_PER_DF + " webhook endpoints"));
                    }
                    String secret = WhkSignature.newSecret();
                    WhkEndpoint endpoint = new WhkEndpoint(UUID.randomUUID().toString(), dfId, cleanUrl,
                            description == null || description.isBlank() ? null : description.strip(), types, true,
                            sealer.seal(secret), null, null, clock.instant());
                    return repo.saveEndpoint(endpoint)
                            .flatMap(saved -> audit.record("webhook.created", dfId, saved.id(),
                                    AuditTrail.details("url", saved.url())).thenReturn(new WithSecret(saved, secret)));
                });
    }

    /** A new signing secret; the old one is still honoured for {@link WhkEndpoint#ROTATION_OVERLAP}. */
    @Transactional
    public Mono<WithSecret> rotateSecret(String dfId, String id) {
        return endpoint(dfId, id).flatMap(endpoint -> {
            String secret = WhkSignature.newSecret();
            return repo.saveEndpoint(endpoint.withSecret(sealer.seal(secret), clock.instant()))
                    .flatMap(saved -> audit.record("webhook.secret-rotated", dfId, saved.id(),
                            AuditTrail.details("url", saved.url())).thenReturn(new WithSecret(saved, secret)));
        });
    }

    @Transactional
    public Mono<WhkEndpoint> setActive(String dfId, String id, boolean active) {
        return endpoint(dfId, id).flatMap(endpoint -> repo.saveEndpoint(endpoint.withActive(active))
                .flatMap(saved -> audit.record(active ? "webhook.enabled" : "webhook.disabled", dfId, saved.id(),
                        AuditTrail.details("url", saved.url())).thenReturn(saved)));
    }

    @Transactional
    public Mono<Void> delete(String dfId, String id) {
        return endpoint(dfId, id).flatMap(endpoint -> repo.deleteEndpoint(dfId, id)
                .then(audit.record("webhook.deleted", dfId, id, AuditTrail.details("url", endpoint.url()))));
    }

    /** A test message to one endpoint, so the fiduciary can check it receives and verifies. */
    @Transactional
    public Mono<WhkDelivery> sendTest(String dfId, String id) {
        return endpoint(dfId, id).flatMap(endpoint -> !endpoint.active()
                ? Mono.error(new DomRuleViolation(409, "webhook-paused", "Resume the endpoint to send it a test"))
                : repo.enqueueFor(endpoint, new WhkMessage(UUID.randomUUID().toString(), WhkMessage.TEST, dfId,
                        clock.instant(), Map.of("message",
                                "A test from the Consent Manager. Check its signature as you would any other."))));
    }

    public Flux<WhkDelivery> deliveries(String dfId, String endpointId, WhkDelivery.Sts sts, int limit) {
        return repo.deliveries(dfId, endpointId, sts, Math.min(Math.max(limit, 1), 200));
    }

    public record DeliveryView(WhkDelivery delivery, List<WhkDelivery.Attempt> attempts) {
    }

    public Mono<DeliveryView> delivery(String dfId, String id) {
        return repo.findDelivery(dfId, id)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "delivery-not-found", "No delivery " + id)))
                .flatMap(d -> repo.attempts(d.id()).collectList().map(a -> new DeliveryView(d, a)));
    }

    /** Sends a delivered or dead-lettered message again, now, with the same id: a receiver de-duplicates on it. */
    @Transactional
    public Mono<WhkDelivery> replay(String dfId, String id) {
        return repo.findDelivery(dfId, id)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "delivery-not-found", "No delivery " + id)))
                .flatMap(d -> d.sts() == WhkDelivery.Sts.PENDING
                        ? Mono.error(new DomRuleViolation(409, "delivery-pending", "This delivery is still being tried"))
                        : repo.saveDelivery(d.replayed(clock.instant()))
                                .flatMap(saved -> audit.record("webhook.replayed", dfId, saved.id(),
                                        AuditTrail.details("type", saved.type())).thenReturn(saved)));
    }
}
