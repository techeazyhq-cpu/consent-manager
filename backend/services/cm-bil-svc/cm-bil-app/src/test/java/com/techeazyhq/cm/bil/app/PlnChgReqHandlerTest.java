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

package com.techeazyhq.cm.bil.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubFactory;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.PendingPlnChgReqExistsException;
import com.techeazyhq.cm.bil.dom.PlnChgReq;
import com.techeazyhq.cm.bil.dom.PlnChgReqRepoPort;
import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.bil.dom.UsgPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** ADR-039: fiduciaries ask for a plan; platform administrators decide. */
class PlnChgReqHandlerTest {

    private static final SubPln FREE = new SubPln("p-free", "FREE", 5, 1000);
    private static final SubPln STANDARD = new SubPln("p-std", "STANDARD", 50, 50000);

    private final Map<String, DfSub> subs = new LinkedHashMap<>();
    private final Map<String, PlnChgReq> reqs = new LinkedHashMap<>();
    private final Map<String, Long> used = new LinkedHashMap<>();

    private final DfSubRepoPort subRepo = new DfSubRepoPort() {
        public Mono<DfSub> save(DfSub s) {
            subs.put(s.id(), s);
            return Mono.just(s);
        }

        public Mono<DfSub> findByDfId(String dfId) {
            return Mono.justOrEmpty(subs.get(dfId));
        }

        public Flux<DfSub> findAll() {
            return Flux.fromIterable(subs.values());
        }
    };

    private final SubPlnRepoPort planRepo = new SubPlnRepoPort() {
        public Mono<SubPln> save(SubPln p) {
            return Mono.just(p);
        }

        public Mono<SubPln> findById(String id) {
            return Flux.just(FREE, STANDARD).filter(p -> p.id().equals(id)).next();
        }

        public Mono<SubPln> findByName(String name) {
            return Flux.just(FREE, STANDARD).filter(p -> p.name().equals(name)).next();
        }

        public Flux<SubPln> findAll() {
            return Flux.just(STANDARD, FREE);
        }
    };

    private final PlnChgReqRepoPort reqRepo = new PlnChgReqRepoPort() {
        public Mono<PlnChgReq> save(PlnChgReq r) {
            boolean another = reqs.values().stream().anyMatch(o -> !o.id().equals(r.id()) && o.dfId().equals(r.dfId())
                    && o.sts() == PlnChgReq.Sts.PENDING && r.sts() == PlnChgReq.Sts.PENDING);
            if (another) {
                return Mono.error(new PendingPlnChgReqExistsException(r.dfId()));
            }
            reqs.put(r.id(), r);
            return Mono.just(r);
        }

        public Mono<PlnChgReq> findById(String id) {
            return Mono.justOrEmpty(reqs.get(id));
        }

        public Mono<PlnChgReq> findPendingByDfId(String dfId) {
            return Flux.fromIterable(reqs.values()).filter(r -> r.dfId().equals(dfId) && r.sts() == PlnChgReq.Sts.PENDING).next();
        }

        public Flux<PlnChgReq> findByDfId(String dfId, int limit) {
            return Flux.fromIterable(reqs.values()).filter(r -> r.dfId().equals(dfId));
        }

        public Flux<PlnChgReq> findBySts(PlnChgReq.Sts sts, int limit) {
            return Flux.fromIterable(reqs.values()).filter(r -> sts == null || r.sts() == sts);
        }
    };

    private final UsgPort usage = new UsgPort() {
        public Mono<Long> currentPeriodUsage(String dfId) {
            return Mono.just(used.getOrDefault(dfId, 0L));
        }

        public Mono<Long> add(String dfId, long count) {
            return Mono.just(used.merge(dfId, count, Long::sum));
        }
    };

    private final PlnChgReqHandler handler = new PlnChgReqHandler(reqRepo, subRepo, planRepo);

    private static final BilCaller OWNER = new BilCaller("u-1", "owner@acme.example", "df-1", true, false, false);
    private static final BilCaller AUDITOR = new BilCaller("u-2", "audit@acme.example", "df-1", false, false, false);
    private static final BilCaller ADMIN = new BilCaller("s-1", "admin@cm.local", null, false, true, true);
    private static final BilCaller OPERATOR = new BilCaller("s-2", "operator@cm.local", null, false, true, false);

    PlnChgReqHandlerTest() {
        subs.put("df-1", DfSubFactory.create("df-1", FREE.id()));
    }

    private static int status(Runnable call) {
        return assertThrows(DomRuleViolation.class, call::run).httpStatus();
    }

    @Test
    void anOwnerAsks_andAnAdministratorsApprovalChangesThePlan() {
        PlnChgReq req = handler.request(OWNER, "STANDARD", "Launching a campaign").block();
        assertEquals("FREE", req.fromPlnName());
        assertEquals(PlnChgReq.Sts.PENDING, req.sts());

        assertEquals(403, status(() -> handler.approve(OPERATOR, req.id(), null).block()), "operators don't decide");
        PlnChgReq approved = handler.approve(ADMIN, req.id(), "Welcome aboard").block();

        assertEquals(PlnChgReq.Sts.APPROVED, approved.sts());
        assertEquals("admin@cm.local", approved.decidedByName());
        assertEquals(STANDARD.id(), subs.get("df-1").subPlnId());
        assertEquals(409, status(() -> handler.decline(ADMIN, req.id(), "no").block()), "decided once");
    }

    @Test
    void oneRequestWaitsAtATime_andWithdrawingAllowsAnother() {
        PlnChgReq first = handler.request(OWNER, "STANDARD", null).block();
        assertEquals(409, status(() -> handler.request(OWNER, "STANDARD", null).block()));

        assertEquals(PlnChgReq.Sts.WITHDRAWN, handler.withdraw(OWNER, first.id()).block().sts());
        assertEquals(PlnChgReq.Sts.PENDING, handler.request(OWNER, "STANDARD", null).block().sts());
    }

    @Test
    void aDeclineSaysWhy_andLeavesThePlan() {
        PlnChgReq req = handler.request(OWNER, "STANDARD", null).block();

        assertEquals(400, status(() -> handler.decline(ADMIN, req.id(), " ").block()));
        PlnChgReq declined = handler.decline(ADMIN, req.id(), "Verify your organisation first").block();

        assertEquals(PlnChgReq.Sts.DECLINED, declined.sts());
        assertEquals("Verify your organisation first", declined.decisionNote());
        assertEquals(FREE.id(), subs.get("df-1").subPlnId());
    }

    @Test
    void refusesWhatCantBeAskedFor() {
        assertEquals(403, status(() -> handler.request(AUDITOR, "STANDARD", null).block()), "not an Owner or Admin");
        assertEquals(403, status(() -> handler.request(ADMIN, "STANDARD", null).block()), "staff have no fiduciary");
        assertEquals(400, status(() -> handler.request(OWNER, "FREE", null).block()), "already on it");
        assertEquals(400, status(() -> handler.request(OWNER, "GOLD", null).block()), "no such plan");
        assertEquals(400, status(() -> handler.request(OWNER, "STANDARD", "x".repeat(501)).block()), "note too long");
        assertEquals(403, status(() -> handler.list(OWNER, null).collectList().block()), "the queue is for staff");
    }

    @Test
    void theOverviewPutsTheTenantsClosestToTheirAllowanceFirst() {
        subs.put("df-2", DfSubFactory.create("df-2", STANDARD.id()));
        subs.put("df-3", DfSubFactory.create("df-3", FREE.id()));
        used.put("df-1", 100L);
        used.put("df-2", 40000L);
        used.put("df-3", 950L);

        List<BilOverviewQryHandler.Row> rows = new BilOverviewQryHandler(subRepo, planRepo, usage).handle().block();

        assertEquals(List.of("df-3", "df-2", "df-1"), rows.stream().map(BilOverviewQryHandler.Row::dfId).toList());
        assertEquals(1000, rows.get(0).sub().reqPerMonthQta());
        assertEquals(List.of("FREE", "STANDARD"), new BilOverviewQryHandler(subRepo, planRepo, usage).plans()
                .map(SubPln::name).collectList().block(), "plans in order of size");
        assertEquals(rows.stream().sorted(Comparator.comparing(BilOverviewQryHandler.Row::dfId)).count(), 3);
    }
}
