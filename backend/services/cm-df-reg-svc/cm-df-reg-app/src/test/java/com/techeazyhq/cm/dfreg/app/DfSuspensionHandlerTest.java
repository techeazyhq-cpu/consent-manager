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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DfSuspensionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-08T09:00:00Z");

    private final Map<String, Df> dfs = new LinkedHashMap<>();
    private final List<String> audited = new ArrayList<>();

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

    private final DfSuspensionHandler handler = new DfSuspensionHandler(repo,
            (action, dfId, target, details) -> {
                audited.add(action + " " + details);
                return Mono.empty();
            }, Clock.fixed(NOW, ZoneOffset.UTC));

    private void df(String id, DfSts sts) {
        dfs.put(id, DfFactory.rehydrate(id, "Acme", "ops@acme.example", DfPrfl.EMPTY, null, sts, NOW, "secret", NOW));
    }

    private static void refused(int status, String type, Runnable action) {
        DomRuleViolation refusal = assertThrows(DomRuleViolation.class, action::run);
        assertEquals(status, refusal.httpStatus());
        assertEquals(type, refusal.problemType());
    }

    @Test
    void anActiveFiduciaryIsSuspendedWithItsReasonAndTheTime() {
        df("df-1", DfSts.ACTIVE);

        Df suspended = handler.suspend("df-1", "  Chargeback fraud, case 4411 ").block();

        assertEquals(DfSts.SUSPENDED, suspended.sts());
        assertEquals("Chargeback fraud, case 4411", suspended.suspensionReason());
        assertEquals(NOW, suspended.suspendedAt());
        assertEquals(DfSts.SUSPENDED, dfs.get("df-1").sts());
        assertEquals(1, audited.size());
        assertTrue(audited.get(0).startsWith("tenant.suspended"));
        assertTrue(audited.get(0).contains("Chargeback fraud"));
    }

    @Test
    void aReasonIsRequiredAndBounded() {
        df("df-1", DfSts.ACTIVE);

        refused(400, "invalid-request", () -> handler.suspend("df-1", null).block());
        refused(400, "invalid-request", () -> handler.suspend("df-1", "   ").block());
        refused(400, "invalid-request", () -> handler.suspend("df-1", "x".repeat(501)).block());
        assertEquals(DfSts.ACTIVE, dfs.get("df-1").sts());
        assertTrue(audited.isEmpty());
    }

    @Test
    void onlyAnActiveFiduciaryCanBeSuspended() {
        df("pending", DfSts.PENDING);
        df("already", DfSts.SUSPENDED);

        refused(409, "df-cannot-suspend", () -> handler.suspend("pending", "reason").block());
        refused(409, "df-cannot-suspend", () -> handler.suspend("already", "reason").block());
        refused(404, "df-not-found", () -> handler.suspend("missing", "reason").block());
    }

    @Test
    void resumingPutsItBackAndForgetsTheReason() {
        df("df-1", DfSts.ACTIVE);
        handler.suspend("df-1", "reason").block();

        Df resumed = handler.resume("df-1").block();

        assertEquals(DfSts.ACTIVE, resumed.sts());
        assertNull(resumed.suspendedAt());
        assertNull(resumed.suspensionReason());
        assertEquals("secret", resumed.cmDfApiClientSecret(), "its client secret is untouched");
        assertEquals(2, audited.size());
        assertTrue(audited.get(1).startsWith("tenant.resumed"));
    }

    @Test
    void onlyASuspendedFiduciaryCanBeResumed() {
        df("df-1", DfSts.ACTIVE);

        refused(409, "df-not-suspended", () -> handler.resume("df-1").block());
        assertFalse(audited.stream().anyMatch(a -> a.startsWith("tenant.resumed")));
    }

    @Test
    void aSuspensionSurvivesBeingStoredAndRead() {
        Df stored = DfFactory.rehydrate("df-1", "Acme", "ops@acme.example", DfPrfl.EMPTY, null, DfSts.SUSPENDED, NOW,
                "secret", NOW, NOW, "why");

        assertEquals("why", stored.suspensionReason());
        assertEquals(NOW, stored.suspendedAt());
        stored.resume();
        assertEquals(DfSts.ACTIVE, stored.sts());
    }
}
