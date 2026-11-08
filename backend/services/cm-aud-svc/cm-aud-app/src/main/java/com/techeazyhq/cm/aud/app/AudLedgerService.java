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

package com.techeazyhq.cm.aud.app;

import com.techeazyhq.cm.aud.dom.AudChainVerifier;
import com.techeazyhq.cm.aud.dom.AudDraft;
import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudLedgerPort;
import com.techeazyhq.cm.aud.dom.AudQuery;
import com.techeazyhq.cm.aud.dom.AudRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;

/** The audit ledger's use cases (ADR-014): record an event, look entries up, verify the whole chain. */
public class AudLedgerService {

    private final AudLedgerPort ledger;
    private final Clock clock;

    public AudLedgerService(AudLedgerPort ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    public Mono<AudEntry> record(AudDraft draft) {
        return ledger.append(draft, clock.instant());
    }

    public Flux<AudEntry> find(AudQuery query) {
        return ledger.find(query);
    }

    public Flux<AudRecord> findRecords(AudQuery query) {
        return ledger.findRecords(query);
    }

    /**
     * A fiduciary's ledger entries with their payloads (null once erased), newest first, for the export it can take when
     * leaving (ADR-064). At most {@code limit}; {@code truncated} says when there were more.
     */
    public Mono<java.util.Map<String, Object>> exportForDf(String dfId, int limit) {
        int cap = Math.max(1, Math.min(limit, 100_000));
        return collect(dfId, cap, null, new java.util.ArrayList<>()).map(all -> {
            boolean truncated = all.size() > cap;
            java.util.List<java.util.Map<String, Object>> items = (truncated ? all.subList(0, cap) : all).stream().map(r -> {
                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                AudEntry e = r.entry();
                m.put("seq", e.seq());
                m.put("eventId", e.evtId().toString());
                m.put("type", e.evtType());
                m.put("occurredAt", e.occurredAt().toString());
                m.put("recordedAt", e.recordedAt().toString());
                m.put("cnsReqId", e.cnsReqId());
                m.put("cnsArtId", e.cnsArtId());
                m.put("payloadSha256", e.payloadSha256());
                m.put("entryHash", e.entryHash());
                m.put("payload", r.payloadJson());
                return m;
            }).toList();
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("count", items.size());
            out.put("truncated", truncated);
            out.put("items", items);
            return out;
        });
    }

    private Mono<java.util.List<AudRecord>> collect(String dfId, int cap, Long before, java.util.List<AudRecord> acc) {
        int want = Math.min(AudQuery.MAX_LIMIT, cap + 1 - acc.size());
        if (want <= 0) {
            return Mono.just(acc);
        }
        return ledger.findRecords(new AudQuery(null, null, dfId, null, want, AudQuery.Category.ALL, before)).collectList().flatMap(page -> {
            acc.addAll(page);
            return page.size() < want ? Mono.just(acc) : collect(dfId, cap, page.get(page.size() - 1).entry().seq(), acc);
        });
    }

    public Mono<AudEntry> head() {
        return ledger.head();
    }

    public Mono<Boolean> concerns(long seq, String dpId) {
        return ledger.concerns(seq, dpId);
    }

    /** Recomputes every link from the first entry; the answer names the first entry that does not hold. */
    public Mono<AudChainVerifier.Verdict> verify() {
        return Mono.defer(() -> {
            AudChainVerifier verifier = new AudChainVerifier();
            return ledger.records().doOnNext(verifier::accept).then(Mono.fromSupplier(verifier::verdict));
        });
    }
}
