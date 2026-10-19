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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnsqry.app.evt.CnsDpLinkedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsGrantedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsNotificationFailedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.CnsReqCreatedEvtPayload;
import com.techeazyhq.cm.cnsqry.app.evt.DpRefPayload;
import com.techeazyhq.cm.cnsqry.app.evt.ScpPayload;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.DpDashboardRow;
import com.techeazyhq.cm.cnsqry.dom.PrjCkptPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Events arrive on separate topics, in any order; none may be lost, and none may undo a newer state (#219). */
class ProjectorTest {

    private static final Instant T0 = Instant.parse("2026-10-19T10:00:00Z");

    private final ReadModel rows = new ReadModel();
    private final Set<String> processed = new HashSet<>();
    private final Projector projector = new Projector(rows, new History(), new Checkpoints());

    @Test
    void aGrantProjectedBeforeItsRequestIsRetriedNotLost() {
        CnsGrantedEvtPayload grant = granted(T0.plusSeconds(5));

        assertThrows(Projector.NotYetProjected.class, () -> projector.onCnsGranted(grant).block());
        assertFalse(processed.contains(grant.evtId()), "a grant that couldn't be applied must come back");

        projector.onCnsReqCreated(created(T0)).block();
        projector.onCnsGranted(grant).block(); // the redelivery

        assertEquals(CnsLcySts.ISSUED, rows.byReq.get("req-1").lcySts());
        assertEquals("art-1", rows.byReq.get("req-1").cnsArtId());
        assertTrue(processed.contains(grant.evtId()));
    }

    @Test
    void aLateNotificationFailureDoesNotUndoAGrant() {
        projector.onCnsReqCreated(created(T0)).block();
        projector.onCnsGranted(granted(T0.plusSeconds(10))).block();

        CnsNotificationFailedEvtPayload late = new CnsNotificationFailedEvtPayload("evt-nf", T0.plusSeconds(5), "req-1",
                "df-1", null);
        projector.onCnsNotificationFailed(late).block();

        assertEquals(CnsLcySts.ISSUED, rows.byReq.get("req-1").lcySts());
        assertTrue(processed.contains("evt-nf"), "a stale event is done with, not retried");
    }

    @Test
    void aLinkProjectedBeforeItsRequestIsRetriedNotLost() {
        CnsDpLinkedEvtPayload link = new CnsDpLinkedEvtPayload("evt-link", T0.plusSeconds(1), "req-1", "CnsReq", "dp-9");

        assertThrows(Projector.NotYetProjected.class, () -> projector.onCnsDpLinked(link).block());
        assertFalse(processed.contains("evt-link"));

        projector.onCnsReqCreated(created(T0)).block();
        projector.onCnsDpLinked(link).block();
        assertEquals("dp-9", rows.byReq.get("req-1").dpId());
    }

    private static CnsReqCreatedEvtPayload created(Instant at) {
        return new CnsReqCreatedEvtPayload("evt-created", at, "req-1", "df-1", new DpRefPayload(null, "EMAIL", "a@example.com"),
                new CnsReqCreatedEvtPayload.PrpsPayload("ORDER_UPDATES"),
                new ScpPayload(List.of(new ScpPayload.DCatPayload("EMAIL"))), "Order updates", "Notice");
    }

    private static CnsGrantedEvtPayload granted(Instant at) {
        return new CnsGrantedEvtPayload("evt-granted", at, "req-1", "art-1", "df-1", null, "token",
                new CnsGrantedEvtPayload.ValPrdPayload(at, at.plusSeconds(86_400)));
    }

    private static final class ReadModel implements CnsArtRdMdlRepoPort {
        final Map<String, CnsArtRdMdl> byReq = new LinkedHashMap<>();

        @Override
        public Mono<CnsArtRdMdl> upsert(CnsArtRdMdl row) {
            byReq.put(row.cnsReqId(), row);
            return Mono.just(row);
        }

        @Override
        public Mono<CnsArtRdMdl> findByCnsReqId(String cnsReqId) {
            return Mono.justOrEmpty(byReq.get(cnsReqId));
        }

        @Override
        public Mono<CnsArtRdMdl> findByCnsArtId(String cnsArtId) {
            return Mono.justOrEmpty(byReq.values().stream().filter(r -> cnsArtId.equals(r.cnsArtId())).findFirst());
        }

        @Override
        public Mono<Void> linkDp(String recordId, String recordType, String newDpId) {
            CnsArtRdMdl row = byReq.get(recordId);
            if (row != null && row.dpId() == null) {
                byReq.put(recordId, row.with(row.cnsArtId(), newDpId, row.lcySts(), row.validFrom(), row.validUntil(),
                        row.updatedAt()));
            }
            return Mono.empty();
        }

        @Override
        public Flux<CnsArtRdMdl> findByDpId(String dpId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<CnsArtRdMdl> findByDfId(String dfId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Mono<Map<CnsLcySts, Long>> countByStsForDfId(String dfId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<CnsArtRdMdl> pageByDfId(String dfId, CnsLcySts sts, int limit, long offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Flux<DpDashboardRow> dashboardFor(String dpId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class History implements CnsHstRdMdlRepoPort {
        final List<CnsHstRdMdl> rows = new ArrayList<>();

        @Override
        public Mono<Void> append(CnsHstRdMdl row) {
            rows.add(row);
            return Mono.empty();
        }

        @Override
        public Flux<CnsHstRdMdl> findByCnsReqId(String cnsReqId) {
            return Flux.fromIterable(rows);
        }
    }

    private final class Checkpoints implements PrjCkptPort {
        @Override
        public Mono<Boolean> alreadyProcessed(String evtId) {
            return Mono.just(processed.contains(evtId));
        }

        @Override
        public Mono<Void> markProcessed(String evtId) {
            processed.add(evtId);
            return Mono.empty();
        }
    }
}
