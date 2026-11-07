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

import java.time.Clock;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Platform staff suspending and resuming a fiduciary (ADR-064). A suspended fiduciary is no longer "active", which every
 * check already asks (an API key is refused, no request can be raised for it), so nothing else has to learn about it. Its
 * keys, team and consents are untouched, so resuming puts it back exactly as it was. Both are in the audit ledger, with
 * the reason.
 */
public class DfSuspensionHandler {

    private final DfRepoPort dfRepo;
    private final AuditTrail audit;
    private final Clock clock;

    public DfSuspensionHandler(DfRepoPort dfRepo, AuditTrail audit, Clock clock) {
        this.dfRepo = dfRepo;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Mono<Df> suspend(String dfId, String reason) {
        return find(dfId).flatMap(df -> {
            try {
                df.suspend(reason, clock.instant());
            } catch (IllegalArgumentException e) {
                return Mono.<Df>error(new DomRuleViolation(400, "invalid-request", e.getMessage()));
            } catch (IllegalStateException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "df-cannot-suspend", e.getMessage()));
            }
            return dfRepo.save(df).flatMap(saved -> audit.record("tenant.suspended", dfId, dfId,
                    AuditTrail.details("reason", saved.suspensionReason())).thenReturn(saved));
        });
    }

    @Transactional
    public Mono<Df> resume(String dfId) {
        return find(dfId).flatMap(df -> {
            try {
                df.resume();
            } catch (IllegalStateException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "df-not-suspended", e.getMessage()));
            }
            return dfRepo.save(df).flatMap(saved -> audit.record("tenant.resumed", dfId, dfId).thenReturn(saved));
        });
    }

    private Mono<Df> find(String dfId) {
        return dfRepo.findById(dfId).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + dfId)));
    }
}
