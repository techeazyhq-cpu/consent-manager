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

package com.techeazyhq.cm.cnsqry.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnsqry.app.evt.CnsGrantedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsNotificationFailedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsReqCreatedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsRevokedEvtPayload;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.CnsStats;
import com.techeazyhq.cm.cnsqry.dom.DpDashboardRow;
import com.techeazyhq.cm.cnsqry.dom.PrjCkptPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The admin view and the projection that feeds its "failed" count, over an in-memory read model. */
class AdminCnsQryHandlerTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    private final Map<String, CnsArtRdMdl> rows = new LinkedHashMap<>();
    private final InMemoryReadModel readModel = new InMemoryReadModel();
    private final AdminCnsQryHandler admin = new AdminCnsQryHandler(readModel);

    @Test
    void countsEveryRequestOfTheFiduciaryByStatus() {
        row("r1", "df-1", "dp-1", CnsLcySts.ISSUED, 1);
        row("r2", "df-1", "dp-2", CnsLcySts.ISSUED, 2);
        row("r3", "df-1", null, CnsLcySts.PENDING, 3);
        row("r4", "df-1", "dp-1", CnsLcySts.REVOKED, 4);
        row("r5", "df-1", null, CnsLcySts.NOTIFICATION_FAILED, 5);
        row("r6", "df-2", "dp-1", CnsLcySts.DENIED, 6);

        CnsStats stats = admin.stats("df-1").block();

        assertEquals(new CnsStats("df-1", 5, 1, 2, 0, 1, 0, 1), stats);
    }

    @Test
    void listsRequestsNewestFirstWithThePrincipalMasked() {
        row("r1", "df-1", "dp-1", CnsLcySts.ISSUED, 1);
        row("r2", "df-1", "dp-1", CnsLcySts.REVOKED, 2);
        row("r3", "df-1", "dp-2", CnsLcySts.PENDING, 3);
        row("r4", "df-1", null, CnsLcySts.PENDING, 4);

        List<AdminCnsReqView> page = admin.requests("df-1", null, 10, 0).collectList().block();

        assertEquals(List.of("r4", "r3", "r2", "r1"), page.stream().map(AdminCnsReqView::cnsReqId).toList());
        assertNull(page.get(0).dpPseudonym(), "not signed up yet");
        assertTrue(page.get(1).dpPseudonym().matches("DP-[0-9A-F]{10}"));
        assertEquals(page.get(2).dpPseudonym(), page.get(3).dpPseudonym(), "the same principal, the same handle");
        assertNotEquals(page.get(1).dpPseudonym(), page.get(2).dpPseudonym());
        assertFalse(page.stream().anyMatch(v -> v.toString().contains("dp-1")), "the principal's id never leaves");
    }

    @Test
    void filtersByStatusAndPages() {
        for (int i = 1; i <= 5; i++) {
            row("r" + i, "df-1", "dp-" + i, CnsLcySts.PENDING, i);
        }
        row("r6", "df-1", "dp-6", CnsLcySts.ISSUED, 6);

        assertEquals(List.of("r4", "r3"), admin.requests("df-1", CnsLcySts.PENDING, 2, 1)
                .map(AdminCnsReqView::cnsReqId).collectList().block());
        assertEquals(6, admin.requests("df-1", null, 1000, 0).count().block(), "the page size is capped, not refused");
    }

    @Test
    void theNoticeTheFiduciaryWroteStaysWithTheConsentThroughItsLife() {
        Projector projector = new Projector(readModel, new NoHistory(), new Checkpoints());
        projector.onCnsReqCreated(new CnsReqCreatedEvtPayload("e1", T0, "r1", "df-1", null,
                new CnsReqCreatedEvtPayload.PrpsPayload("ORDER_FULFILLMENT"), null, "Order updates",
                "We email you about your orders until they arrive.")).block();
        projector.onCnsGranted(new CnsGrantedEvtPayload("e2", T0.plusSeconds(5), "r1", "a1", "df-1", null, "tkn",
                new CnsGrantedEvtPayload.ValPrdPayload(T0, T0.plusSeconds(3600)), null, null)).block();
        projector.onCnsRevoked(new CnsRevokedEvtPayload("e3", T0.plusSeconds(9), "a1", null, "df-1")).block();

        CnsArtRdMdl revoked = rows.get("r1");
        assertEquals(CnsLcySts.REVOKED, revoked.lcySts());
        assertEquals("Order updates", revoked.ntcTitle());
        assertEquals("We email you about your orders until they arrive.", revoked.ntcText());
    }

    @Test
    void aRequestFromBeforeNoticesWereProjectedHasNone() {
        Projector projector = new Projector(readModel, new NoHistory(), new Checkpoints());
        projector.onCnsReqCreated(new CnsReqCreatedEvtPayload("e1", T0, "r1", "df-1", null,
                new CnsReqCreatedEvtPayload.PrpsPayload("ORDER_FULFILLMENT"), null, null, null)).block();

        assertNull(rows.get("r1").ntcTitle());
    }

    @Test
    void aRequestWhosePrincipalCouldNotBeReachedIsProjectedAsFailed() {
        Projector projector = new Projector(readModel, new NoHistory(), new Checkpoints());
        projector.onCnsReqCreated(new CnsReqCreatedEvtPayload("e1", T0, "r1", "df-1", null,
                new CnsReqCreatedEvtPayload.PrpsPayload("ORDER_FULFILLMENT"), null, null, null)).block();

        projector.onCnsNotificationFailed(new CnsNotificationFailedEvtPayload("e2", T0.plusSeconds(5), "r1", "df-1", null))
                .block();
        projector.onCnsNotificationFailed(new CnsNotificationFailedEvtPayload("e2", T0.plusSeconds(5), "r1", "df-1", null))
                .block();

        assertEquals(CnsLcySts.NOTIFICATION_FAILED, rows.get("r1").lcySts());
        assertEquals(1, admin.stats("df-1").block().notificationFailed());
    }

    private void row(String cnsReqId, String dfId, String dpId, CnsLcySts sts, int minute) {
        Instant at = T0.plusSeconds(60L * minute);
        rows.put(cnsReqId, new CnsArtRdMdl(cnsReqId, null, dfId, dpId, "ORDER_FULFILLMENT", Set.of("EMAIL"), sts,
                null, null, at, at, "Order updates", "We email you about your orders.", null));
    }

    private final class InMemoryReadModel implements CnsArtRdMdlRepoPort {
        @Override
        public Mono<CnsArtRdMdl> upsert(CnsArtRdMdl row) {
            rows.put(row.cnsReqId(), row);
            return Mono.just(row);
        }

        @Override
        public Mono<CnsArtRdMdl> findByCnsReqId(String cnsReqId) {
            return Mono.justOrEmpty(rows.get(cnsReqId));
        }

        @Override
        public Mono<CnsArtRdMdl> findByCnsArtId(String cnsArtId) {
            return Mono.justOrEmpty(rows.values().stream().filter(r -> cnsArtId.equals(r.cnsArtId())).findFirst());
        }

        @Override
        public Flux<CnsArtRdMdl> findByDpId(String dpId) {
            return Flux.empty();
        }

        @Override
        public Flux<CnsArtRdMdl> findByDfId(String dfId) {
            return Flux.fromIterable(new ArrayList<>(rows.values())).filter(r -> r.dfId().equals(dfId));
        }

        @Override
        public Mono<Map<CnsLcySts, Long>> countByStsForDfId(String dfId) {
            return findByDfId(dfId).collectList()
                    .map(list -> list.stream().collect(Collectors.groupingBy(CnsArtRdMdl::lcySts, Collectors.counting())));
        }

        @Override
        public Flux<CnsArtRdMdl> pageByDfId(String dfId, CnsLcySts sts, int limit, long offset) {
            return findByDfId(dfId)
                    .filter(r -> sts == null || r.lcySts() == sts)
                    .sort(Comparator.comparing(CnsArtRdMdl::createdAt).reversed())
                    .skip(offset)
                    .take(limit);
        }

        @Override
        public Mono<Void> linkDp(String recordId, String recordType, String newDpId) {
            return Mono.empty();
        }

        @Override
        public Flux<DpDashboardRow> dashboardFor(String dpId) {
            return Flux.empty();
        }
    }

    private static final class NoHistory implements CnsHstRdMdlRepoPort {
        @Override
        public Mono<Void> append(CnsHstRdMdl row) {
            return Mono.empty();
        }

        @Override
        public Flux<CnsHstRdMdl> findByCnsReqId(String cnsReqId) {
            return Flux.empty();
        }
    }

    private static final class Checkpoints implements PrjCkptPort {
        private final Set<String> processed = new HashSet<>();

        @Override
        public Mono<Boolean> alreadyProcessed(String evtId) {
            return Mono.just(processed.contains(evtId));
        }

        @Override
        public Mono<Void> markProcessed(String evtId) {
            return Mono.fromRunnable(() -> processed.add(evtId));
        }
    }
}
