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
import java.time.Duration;
import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfTermsRepoPort;
import com.techeazyhq.cm.dfreg.dom.RecordsPurgePort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Retention for fiduciaries that have left (ADR-064). A closed fiduciary's records are kept for the retention period
 * (7 years by default) and then deleted by the services that hold them; its acceptances of the terms go with them. A legal
 * hold on a fiduciary stops all of it until released. The consent events, receipts and audit entries themselves are
 * append-only; only the purge, through each service's own guarded path, may delete them, and the ledger keeps its entries.
 */
public class DfRetentionHandler {

    private final DfRepoPort dfs;
    private final RecordsPurgePort records;
    private final DfTermsRepoPort terms;
    private final AuditTrail audit;
    private final Clock clock;
    private final Duration retention;

    public DfRetentionHandler(DfRepoPort dfs, RecordsPurgePort records, DfTermsRepoPort terms, AuditTrail audit, Clock clock,
                              Duration retention) {
        this.dfs = dfs;
        this.records = records;
        this.terms = terms;
        this.audit = audit;
        this.clock = clock;
        this.retention = retention;
    }

    /** What is older than this is past its retention. */
    public Instant cutoff() {
        return clock.instant().minus(retention);
    }

    /** The closed fiduciaries whose retention has passed, that are not held and not yet purged. */
    public Flux<String> due() {
        Instant cutoff = cutoff();
        return dfs.findAll().filter(df -> df.purgeable(cutoff)).map(Df::id);
    }

    /** Purges one fiduciary. When nothing of its is left, it is marked purged and not looked at again. */
    public Mono<Long> purge(String dfId) {
        Instant cutoff = cutoff();
        return dfs.findById(dfId).filter(df -> df.purgeable(cutoff)).flatMap(df -> records.purge(dfId, cutoff).flatMap(done -> {
            if (done.remaining() > 0) {
                return Mono.just(done.deleted());
            }
            return terms.deleteByDfId(dfId).flatMap(acceptances -> finish(dfId, done.deleted(), acceptances));
        }));
    }

    @Transactional
    Mono<Long> finish(String dfId, long deleted, long acceptances) {
        return dfs.findById(dfId).filter(df -> df.purgeable(cutoff())).flatMap(df -> {
            df.markPurged(clock.instant());
            return dfs.save(df).flatMap(saved -> audit.record("tenant.purged", dfId, dfId, AuditTrail.details(
                    "recordsDeleted", String.valueOf(deleted), "acceptancesDeleted", String.valueOf(acceptances)))
                    .thenReturn(deleted));
        }).defaultIfEmpty(deleted);
    }

    @Transactional
    public Mono<Df> placeLegalHold(String dfId, String reason) {
        return find(dfId).flatMap(df -> {
            try {
                df.placeLegalHold(reason, clock.instant());
            } catch (IllegalArgumentException e) {
                return Mono.<Df>error(new DomRuleViolation(400, "invalid-request", e.getMessage()));
            }
            return dfs.save(df).flatMap(saved -> audit.record("tenant.legal-hold-placed", dfId, dfId,
                    AuditTrail.details("reason", saved.legalHoldReason())).thenReturn(saved));
        });
    }

    @Transactional
    public Mono<Df> releaseLegalHold(String dfId) {
        return find(dfId).flatMap(df -> {
            try {
                df.releaseLegalHold();
            } catch (IllegalStateException e) {
                return Mono.<Df>error(new DomRuleViolation(409, "df-no-legal-hold", e.getMessage()));
            }
            return dfs.save(df).flatMap(saved -> audit.record("tenant.legal-hold-released", dfId, dfId).thenReturn(saved));
        });
    }

    private Mono<Df> find(String dfId) {
        return dfs.findById(dfId).switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-not-found", "No DF " + dfId)));
    }
}
