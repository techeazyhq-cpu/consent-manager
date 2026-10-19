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

package com.techeazyhq.cm.dfreg.app;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.DfApiKey;
import com.techeazyhq.cm.dfreg.dom.DfApiKeyRepoPort;
import com.techeazyhq.cm.dfreg.dom.SecretSealerPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A fiduciary's API keys (ADR-041), managed by its Owners, Administrators and Developers from the DF Console. Each
 * change is in the audit ledger (ADR-040). The key and signing secret are returned once, when issued.
 */
public class DfApiKeyHandler {

    private final DfApiKeyRepoPort keys;
    private final SecretSealerPort sealer;
    private final AuditTrail audit;

    public DfApiKeyHandler(DfApiKeyRepoPort keys, SecretSealerPort sealer, AuditTrail audit) {
        this.keys = keys;
        this.sealer = sealer;
        this.audit = audit;
    }

    public Flux<DfApiKey> list(DfTeamCaller caller) {
        return require(caller).thenMany(keys.findByDfId(caller.dfId()));
    }

    @Transactional
    public Mono<DfApiKey.Issued> issue(DfTeamCaller caller, String label, boolean signingRequired) {
        return require(caller)
                .then(Mono.fromCallable(() -> DfApiKey.issue(caller.dfId(), label, signingRequired, caller.id(), sealer)))
                .onErrorMap(IllegalArgumentException.class, e -> new DomRuleViolation(400, "invalid-api-key", e.getMessage()))
                .flatMap(issued -> keys.save(issued.key())
                        .then(audit.record("api-key.issued", caller.dfId(), issued.key().id(), AuditTrail.details(
                                "key", issued.key().keyPrefix() + "…", "name", issued.key().label(),
                                "signingRequired", String.valueOf(signingRequired))))
                        .thenReturn(issued));
    }

    @Transactional
    public Mono<DfApiKey> revoke(DfTeamCaller caller, String id) {
        return own(caller, id).flatMap(key -> {
            key.revoke();
            return keys.save(key).flatMap(saved -> audit.record("api-key.revoked", caller.dfId(), id,
                    AuditTrail.details("key", saved.keyPrefix() + "…", "name", saved.label())).thenReturn(saved));
        });
    }

    @Transactional
    public Mono<DfApiKey> requireSigning(DfTeamCaller caller, String id, boolean required) {
        return own(caller, id).flatMap(key -> {
            try {
                key.requireSigning(required);
            } catch (IllegalStateException e) {
                return Mono.error(new DomRuleViolation(409, "api-key-revoked", e.getMessage()));
            }
            return keys.save(key).flatMap(saved -> audit.record(required ? "api-key.signing-required" : "api-key.signing-optional",
                    caller.dfId(), id, AuditTrail.details("key", saved.keyPrefix() + "…", "name", saved.label())).thenReturn(saved));
        });
    }

    private Mono<DfApiKey> own(DfTeamCaller caller, String id) {
        return require(caller).then(keys.findById(id))
                .filter(key -> key.dfId().equals(caller.dfId()))
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "api-key-not-found", "No API key " + id)));
    }

    private static Mono<Void> require(DfTeamCaller caller) {
        if (caller.platformStaff() || caller.role() == null || caller.dfId() == null || !caller.role().managesApiKeys()) {
            return Mono.error(new DomRuleViolation(403, "not-permitted",
                    "Owners, Administrators and Developers manage the organisation's API keys"));
        }
        return Mono.empty();
    }
}
