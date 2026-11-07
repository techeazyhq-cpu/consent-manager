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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.DfTermsAcceptance;
import com.techeazyhq.cm.dfreg.dom.DfTermsRepoPort;
import com.techeazyhq.cm.dfreg.dom.RecordsPurgePort;
import com.techeazyhq.cm.dfreg.dom.TermsDoc;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfRetentionHandlerTest {

    private static final Instant NOW = Instant.parse("2034-01-01T00:00:00Z");
    private static final Duration SEVEN_YEARS = Duration.ofDays(2555);

    private final Map<String, Df> dfs = new LinkedHashMap<>();
    private final List<String> audited = new ArrayList<>();
    private final List<Instant> cutoffsAsked = new ArrayList<>();
    private final List<DfTermsAcceptance> acceptances = new ArrayList<>();
    private long remaining;
    private boolean purgeFails;

    private final DfRepoPort repo = new DfRepoPort() {
        @Override
        public Mono<Df> save(Df df) {
            dfs.put(df.id(), df);
            return Mono.just(df);
        }

        @Override
        public Mono<Df> findById(String id) {
            return Mono.justOrEmpty(dfs.get(id));
        }

        @Override
        public Flux<Df> findAll() {
            return Flux.fromIterable(dfs.values());
        }
    };

    private final RecordsPurgePort records = (dfId, before) -> {
        if (purgeFails) {
            return Mono.error(new IllegalStateException("cns-qry down"));
        }
        cutoffsAsked.add(before);
        return Mono.just(new RecordsPurgePort.Result(5, remaining));
    };

    private final DfTermsRepoPort terms = new DfTermsRepoPort() {
        @Override
        public Mono<DfTermsAcceptance> saveIfAbsent(DfTermsAcceptance a) {
            return Mono.just(a);
        }

        @Override
        public Mono<Long> deleteByDfId(String dfId) {
            long n = acceptances.size();
            acceptances.clear();
            return Mono.just(n);
        }

        @Override
        public Flux<DfTermsAcceptance> findByDfId(String dfId) {
            return Flux.fromIterable(acceptances);
        }
    };

    private final DfRetentionHandler handler = new DfRetentionHandler(repo, records, terms,
            (action, dfId, target, details) -> {
                audited.add(action + " " + details);
                return Mono.empty();
            }, Clock.fixed(NOW, ZoneOffset.UTC), SEVEN_YEARS);

    private void closed(String id, Instant closedAt) {
        Df df = DfFactory.rehydrate(id, "Acme", "[erased]", DfPrfl.EMPTY, null, DfSts.CLOSED, NOW.minus(Duration.ofDays(4000)),
                null, null, null, null, DfSts.ACTIVE, null, null, null, 0, closedAt, null, null, null);
        dfs.put(id, df);
    }

    @Test
    void onlyClosedFiduciariesOlderThanTheRetentionAreDue() {
        closed("old", NOW.minus(SEVEN_YEARS).minusSeconds(1));
        closed("recent", NOW.minus(Duration.ofDays(365)));
        closed("exactly", NOW.minus(SEVEN_YEARS));
        dfs.put("active", DfFactory.rehydrate("active", "A", "a@a.example", DfPrfl.EMPTY, null, DfSts.ACTIVE, NOW, "s", NOW));

        assertEquals(List.of("old"), handler.due().collectList().block());
    }

    @Test
    void aLegalHoldStopsEverythingUntilItIsReleased() {
        closed("held", NOW.minus(SEVEN_YEARS).minusSeconds(60));
        handler.placeLegalHold("held", "  Regulator inquiry 77 ").block();

        assertEquals("Regulator inquiry 77", dfs.get("held").legalHoldReason());
        assertTrue(handler.due().collectList().block().isEmpty());
        assertNull(handler.purge("held").block(), "nothing is asked of the services while it is held");
        assertTrue(cutoffsAsked.isEmpty());

        handler.releaseLegalHold("held").block();
        assertEquals(List.of("held"), handler.due().collectList().block());
        assertTrue(audited.get(0).startsWith("tenant.legal-hold-placed"));
        assertTrue(audited.get(1).startsWith("tenant.legal-hold-released"));
    }

    @Test
    void aHoldNeedsAReasonAndAReleaseNeedsAHold() {
        closed("df", NOW.minus(SEVEN_YEARS).minusSeconds(60));

        DomRuleViolation noReason = assertThrows(DomRuleViolation.class, () -> handler.placeLegalHold("df", " ").block());
        assertEquals(400, noReason.httpStatus());
        DomRuleViolation noHold = assertThrows(DomRuleViolation.class, () -> handler.releaseLegalHold("df").block());
        assertEquals(409, noHold.httpStatus());
        assertEquals("df-no-legal-hold", noHold.problemType());
        assertEquals(404, assertThrows(DomRuleViolation.class, () -> handler.placeLegalHold("missing", "x").block()).httpStatus());
    }

    @Test
    void thePurgeAsksForRecordsOlderThanTheCutoffAndMarksTheFiduciaryWhenNothingIsLeft() {
        closed("df", NOW.minus(SEVEN_YEARS).minusSeconds(60));
        acceptances.add(DfTermsAcceptance.of("df", TermsDoc.TERMS, "2026-11", "owner", NOW));
        remaining = 0;

        assertEquals(5L, handler.purge("df").block());

        assertEquals(List.of(NOW.minus(SEVEN_YEARS)), cutoffsAsked);
        assertNotNull(dfs.get("df").purgedAt());
        assertTrue(acceptances.isEmpty(), "its acceptances of the terms go with its records");
        assertTrue(audited.get(0).startsWith("tenant.purged"));
        assertTrue(handler.due().collectList().block().isEmpty(), "and it is not looked at again");
    }

    @Test
    void whileSomethingRemainsNothingIsMarkedAndTheAcceptancesStay() {
        closed("df", NOW.minus(SEVEN_YEARS).minusSeconds(60));
        acceptances.add(DfTermsAcceptance.of("df", TermsDoc.DPA, "2026-11", "owner", NOW));
        remaining = 12;

        handler.purge("df").block();

        assertNull(dfs.get("df").purgedAt());
        assertEquals(1, acceptances.size());
        assertTrue(audited.isEmpty());
        assertEquals(List.of("df"), handler.due().collectList().block(), "tried again on the next pass");
    }

    @Test
    void aFailingServiceLeavesItForTheNextPass() {
        closed("df", NOW.minus(SEVEN_YEARS).minusSeconds(60));
        purgeFails = true;

        assertThrows(IllegalStateException.class, () -> handler.purge("df").block());

        assertNull(dfs.get("df").purgedAt());
        assertEquals(List.of("df"), handler.due().collectList().block());
    }

    @Test
    void aFiduciaryThatIsNotClosedCannotBeMarkedPurged() {
        Df active = DfFactory.rehydrate("a", "A", "a@a.example", DfPrfl.EMPTY, null, DfSts.ACTIVE, NOW, "s", NOW);

        assertThrows(IllegalStateException.class, () -> active.markPurged(NOW));
    }
}
