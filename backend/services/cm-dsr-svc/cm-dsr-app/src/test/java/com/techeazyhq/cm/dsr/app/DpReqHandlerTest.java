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

package com.techeazyhq.cm.dsr.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dsr.dom.DpCnsPort;
import com.techeazyhq.cm.dsr.dom.DpReq;
import com.techeazyhq.cm.dsr.dom.DpReqEvtPort;
import com.techeazyhq.cm.dsr.dom.DpReqRepoPort;
import com.techeazyhq.cm.dsr.dom.OpenDpReqExistsException;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DpReqHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    private final InMemoryReqs reqs = new InMemoryReqs();
    private final List<DpReqEvtPort.DfMessage> messages = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();
    /** The principal dp-1 holds consent cns-1 with df-1; anything else is not theirs. */
    private final DpCnsPort consents = (cnsReqId, token) -> "cns-1".equals(cnsReqId) && "dp-1-token".equals(token)
            ? Mono.just(new DpCnsPort.DpCns("cns-1", "df-1", "MARKETING"))
            : Mono.empty();
    private final DpReqHandler handler = new DpReqHandler(reqs, consents,
            m -> Mono.fromRunnable(() -> messages.add(m)),
            (action, dfId, target, details) -> Mono.fromRunnable(() -> audited.add(action + ":" + dfId)),
            DsrDeadlines.DPDPA, Clock.fixed(NOW, ZoneOffset.UTC));

    private static final DsrCaller DP = new DsrCaller("dp-1", "p@example.com", "dp-1", null, false, false, "dp-1-token");
    private static final DsrCaller OTHER_DP = new DsrCaller("dp-2", "q@example.com", "dp-2", null, false, false, "dp-2-token");
    private static final DsrCaller DF_ADMIN = new DsrCaller("u-1", "admin@df1", null, "df-1", true, false, "t");
    private static final DsrCaller DF_AUDITOR = new DsrCaller("u-2", "aud@df1", null, "df-1", false, false, "t");
    private static final DsrCaller OTHER_DF = new DsrCaller("u-3", "admin@df2", null, "df-2", true, false, "t");
    private static final DsrCaller STAFF = new DsrCaller("s-1", "ops@cm", null, null, false, true, "t");

    @Test
    void aRelayedRequestGoesToTheConsentsFiduciaryWithoutThePrincipalsWords() {
        DpReq req = handler.relay(DP, "cns-1", "correction", "My address changed").block();

        assertEquals("df-1", req.dfId());
        assertEquals("MARKETING", req.prpsCode());
        assertEquals(NOW.plusSeconds(90L * 86400), req.dueBy());
        assertEquals(1, messages.size());
        DpReqEvtPort.DfMessage m = messages.get(0);
        assertEquals(DpReqEvtPort.RECEIVED, m.type());
        assertEquals("df-1", m.dfId());
        assertEquals("CORRECTION", m.data().get("right"));
        assertTrue(m.data().values().stream().noneMatch("My address changed"::equals));
        assertEquals(List.of("dp-request.raised:df-1"), audited);
    }

    @Test
    void someoneElsesConsentIsNotFound() {
        problem(404, "cns-not-found", handler.relay(OTHER_DP, "cns-1", "ACCESS", null));
        problem(400, "invalid-right", handler.relay(DP, "cns-1", "FORGET", null));
        assertTrue(messages.isEmpty());
    }

    @Test
    void theSameRequestCantBeOpenTwice() {
        handler.relay(DP, "cns-1", "ACCESS", null).block();
        problem(409, "dp-request-open", handler.relay(DP, "cns-1", "ACCESS", null));
    }

    @Test
    void onlyAPrincipalRaises() {
        problem(403, "not-a-data-principal", handler.grievance(DF_ADMIN, "Nope"));
    }

    @Test
    void aPrincipalCantFloodTheSystem() {
        for (int i = 0; i < DpReqHandler.MAX_OPEN; i++) {
            handler.grievance(DP, "Complaint " + i).block();
        }
        problem(429, "too-many-open-requests", handler.grievance(DP, "One more"));
    }

    @Test
    void theFiduciaryAnswersItsOwnRequestsOnly() {
        DpReq req = handler.relay(DP, "cns-1", "ERASURE", null).block();

        problem(404, "dp-request-not-found", handler.oneForFiduciary(OTHER_DF, req.id()));
        problem(403, "may-not-answer", handler.resolve(DF_AUDITOR, DpReq.Knd.RELAY, req.id(), "Done"));
        problem(403, "not-platform-staff", handler.resolve(DF_ADMIN, DpReq.Knd.GRIEVANCE, req.id(), "Done"));
        problem(400, "invalid-answer", handler.refuse(DF_ADMIN, DpReq.Knd.RELAY, req.id(), "No", null));

        DpReq refused = handler.refuse(DF_ADMIN, DpReq.Knd.RELAY, req.id(), "We keep invoices for 8 years",
                "Income-tax Act retention").block();
        assertEquals(DpReq.Sts.REFUSED, refused.sts());
        assertEquals("admin@df1", refused.closedByName());
        assertEquals(1, handler.forFiduciary(DF_AUDITOR, null).count().block());
        problem(409, "dp-request-closed", handler.withdraw(DP, req.id()));
    }

    @Test
    void staffAnswerGrievancesButNotRelayedRequests() {
        DpReq relayed = handler.relay(DP, "cns-1", "ACCESS", null).block();
        DpReq grievance = handler.grievance(DP, "I was sent the same request five times").block();

        problem(404, "dp-request-not-found", handler.acknowledge(STAFF, DpReq.Knd.GRIEVANCE, relayed.id()));
        assertEquals(DpReq.Sts.ACKNOWLEDGED, handler.acknowledge(STAFF, DpReq.Knd.GRIEVANCE, grievance.id()).block().sts());
        assertEquals(1, handler.grievances(STAFF, null).count().block());
        problem(403, "not-platform-staff", handler.grievances(DF_ADMIN, null).then());
    }

    @Test
    void withdrawingARelayedRequestTellsTheFiduciary() {
        DpReq req = handler.relay(DP, "cns-1", "ACCESS", null).block();

        problem(404, "dp-request-not-found", handler.withdraw(OTHER_DP, req.id()));
        assertEquals(DpReq.Sts.WITHDRAWN, handler.withdraw(DP, req.id()).block().sts());

        assertEquals(List.of(DpReqEvtPort.RECEIVED, DpReqEvtPort.WITHDRAWN),
                messages.stream().map(DpReqEvtPort.DfMessage::type).toList());
        // once withdrawn, the same request can be made again
        handler.relay(DP, "cns-1", "ACCESS", null).block();
    }

    private static void problem(int status, String type, Mono<?> call) {
        try {
            call.block();
            fail("expected " + type);
        } catch (DomRuleViolation e) {
            assertEquals(status, e.httpStatus(), e.getMessage());
            assertEquals(type, e.problemType());
        }
    }

    /** Keeps the unique rule of {@code uq_dp_req_open}. */
    static final class InMemoryReqs implements DpReqRepoPort {

        private final Map<String, DpReq> rows = new ConcurrentHashMap<>();

        @Override
        public Mono<DpReq> save(DpReq req) {
            boolean clash = req.knd() == DpReq.Knd.RELAY && req.sts().open() && rows.values().stream()
                    .anyMatch(r -> !r.id().equals(req.id()) && r.knd() == DpReq.Knd.RELAY && r.sts().open()
                            && r.dpId().equals(req.dpId()) && r.cnsReqId().equals(req.cnsReqId())
                            && r.rgt() == req.rgt());
            if (clash) {
                return Mono.error(new OpenDpReqExistsException());
            }
            rows.put(req.id(), req);
            return Mono.just(req);
        }

        @Override
        public Mono<DpReq> findById(String id) {
            return Mono.justOrEmpty(rows.get(id));
        }

        @Override
        public Flux<DpReq> findByDpId(String dpId, int limit) {
            return Flux.fromStream(rows.values().stream().filter(r -> r.dpId().equals(dpId))
                    .sorted(Comparator.comparing(DpReq::receivedAt).reversed()).limit(limit));
        }

        @Override
        public Flux<DpReq> findOpenByDpId(String dpId) {
            return Flux.fromStream(rows.values().stream().filter(r -> r.dpId().equals(dpId) && r.sts().open()));
        }

        @Override
        public Mono<Long> eraseTextOf(String dpId) {
            List<DpReq> mine = rows.values().stream().filter(r -> r.dpId().equals(dpId)).toList();
            mine.forEach(r -> rows.put(r.id(), DpReq.rehydrate(r.id(), r.knd(), r.dpId(), r.dfId(), r.cnsReqId(),
                    r.prpsCode(), r.rgt(), null, r.receivedAt(), r.dueBy(), r.sts(), r.acknowledgedAt(), r.closedAt(),
                    r.closedBy(), r.closedByName(), null, r.refusalGround())));
            return Mono.just((long) mine.size());
        }

        @Override
        public Mono<Long> countOpenByDpId(String dpId) {
            return Mono.just(rows.values().stream().filter(r -> r.dpId().equals(dpId) && r.sts().open()).count());
        }

        @Override
        public Flux<DpReq> findByDfId(String dfId, DpReq.Sts sts, int limit) {
            return Flux.fromStream(rows.values().stream()
                    .filter(r -> r.knd() == DpReq.Knd.RELAY && dfId.equals(r.dfId()) && (sts == null || r.sts() == sts))
                    .limit(limit));
        }

        @Override
        public Flux<DpReq> findGrievances(DpReq.Sts sts, int limit) {
            return Flux.fromStream(rows.values().stream()
                    .filter(r -> r.knd() == DpReq.Knd.GRIEVANCE && (sts == null || r.sts() == sts))
                    .limit(limit));
        }
    }
}
