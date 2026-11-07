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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubFactory;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.bil.dom.UsgAlert;
import com.techeazyhq.cm.bil.dom.UsgAlertRepoPort;
import com.techeazyhq.cm.bil.dom.UsgPort;
import com.techeazyhq.cm.bil.dom.UsgThresholdEvt;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class UsgAlertSweepTest {

    private static final SubPln FREE = new SubPln("pln-free", "FREE", 5, 1_000);
    private static final SubPln NONE = new SubPln("pln-none", "NONE", 5, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC);

    private final Map<String, DfSub> subs = new ConcurrentHashMap<>();
    private final Map<String, Long> usage = new ConcurrentHashMap<>();
    private final List<UsgAlert> stored = new ArrayList<>();
    private final List<UsgThresholdEvt> events = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();

    private final UsgAlertRepoPort alerts = new UsgAlertRepoPort() {
        @Override
        public Mono<Boolean> addIfNew(UsgAlert a) {
            boolean exists = stored.stream().anyMatch(s -> s.dfId().equals(a.dfId()) && s.period().equals(a.period())
                    && s.threshold() == a.threshold());
            if (!exists) {
                stored.add(a);
            }
            return Mono.just(!exists);
        }

        @Override
        public Flux<UsgAlert> findByDf(String dfId, int limit) {
            return Flux.fromStream(stored.stream().filter(a -> a.dfId().equals(dfId))
                    .sorted(Comparator.comparing(UsgAlert::raisedAt).reversed()).limit(limit));
        }

        @Override
        public Flux<UsgAlert> findByPeriod(String period) {
            return Flux.fromStream(stored.stream().filter(a -> a.period().equals(period)));
        }
    };

    private final UsgAlertHandler handler = new UsgAlertHandler(alerts,
            evt -> Mono.fromRunnable(() -> events.add(evt)),
            (action, dfId, target, details) -> Mono.fromRunnable(() -> audited.add(action + " " + dfId + " " + target)));

    private final UsgAlertSweep sweep = new UsgAlertSweep(new DfSubRepoPort() {
        @Override
        public Mono<DfSub> save(DfSub s) {
            subs.put(s.id(), s);
            return Mono.just(s);
        }

        @Override
        public Mono<DfSub> findByDfId(String dfId) {
            return Mono.justOrEmpty(subs.get(dfId));
        }

        @Override
        public Flux<DfSub> findAll() {
            return Flux.fromIterable(subs.values());
        }
    }, new SubPlnRepoPort() {
        @Override
        public Mono<SubPln> save(SubPln p) {
            return Mono.just(p);
        }

        @Override
        public Mono<SubPln> findById(String id) {
            return Flux.just(FREE, NONE).filter(p -> p.id().equals(id)).next();
        }

        @Override
        public Mono<SubPln> findByName(String name) {
            return Mono.empty();
        }

        @Override
        public Flux<SubPln> findAll() {
            return Flux.just(FREE, NONE);
        }
    }, new UsgPort() {
        @Override
        public Mono<Long> currentPeriodUsage(String dfId) {
            return Mono.just(usage.getOrDefault(dfId, 0L));
        }

        @Override
        public Mono<Long> add(String dfId, long count) {
            return Mono.just(usage.merge(dfId, count, Long::sum));
        }
    }, handler, CLOCK);

    private void fiduciary(String dfId, SubPln plan, long used) {
        subs.put(dfId, DfSubFactory.create(dfId, plan.id()));
        usage.put(dfId, used);
    }

    @Test
    void aFiduciaryAtEightyPercentIsToldOnceWhateverTheSweepCount() {
        fiduciary("df-1", FREE, 799);
        assertEquals(0L, sweep.run().block());

        usage.put("df-1", 800L);
        assertEquals(1L, sweep.run().block());
        assertEquals(0L, sweep.run().block());
        assertEquals(0L, sweep.run().block());

        assertEquals(1, events.size());
        UsgThresholdEvt evt = events.get(0);
        assertEquals("df-1", evt.dfId());
        assertEquals(80, evt.threshold());
        assertEquals("2026-11", evt.period());
        assertEquals(800, evt.used());
        assertEquals(1_000, evt.quota());
        assertEquals("FREE", evt.planName());
        assertEquals(Instant.parse("2026-12-01T00:00:00Z"), evt.resetsAt());
        assertEquals(List.of("usage.alert df-1 2026-11/80"), audited);
    }

    @Test
    void reachingTheAllowanceIsASecondAlertAfterEighty() {
        fiduciary("df-1", FREE, 850);
        sweep.run().block();
        usage.put("df-1", 1_000L);

        assertEquals(1L, sweep.run().block());

        assertEquals(List.of(80, 100), events.stream().map(UsgThresholdEvt::threshold).toList());
        assertEquals(List.of(80, 100), stored.stream().map(UsgAlert::threshold).toList());
    }

    @Test
    void aJumpStraightToTheAllowanceRaisesOnlyThatOne() {
        fiduciary("df-1", FREE, 500);
        sweep.run().block();
        usage.put("df-1", 1_100L);

        assertEquals(1L, sweep.run().block());
        assertEquals(1L, events.size());
        assertEquals(100, events.get(0).threshold());
        assertEquals(1_100, events.get(0).used());
    }

    @Test
    void aSuspendedFiduciaryOrAPlanOfNothingIsNotAlerted() {
        fiduciary("df-suspended", FREE, 999);
        subs.get("df-suspended").suspend();
        fiduciary("df-none", NONE, 50);

        assertEquals(0L, sweep.run().block());
        assertTrue(events.isEmpty());
    }

    @Test
    void eachFiduciaryHasItsOwnAlerts() {
        fiduciary("df-1", FREE, 900);
        fiduciary("df-2", FREE, 100);
        fiduciary("df-3", FREE, 1_000);

        assertEquals(2L, sweep.run().block());

        assertEquals(List.of("df-1", "df-3"), events.stream().map(UsgThresholdEvt::dfId).sorted().toList());
    }

    @Test
    void aFiduciaryOnlySeesItsOwnAlertsAndStaffSeeEveryones() {
        fiduciary("df-1", FREE, 900);
        fiduciary("df-3", FREE, 1_000);
        sweep.run().block();
        UsgAlertQryHandler qry = new UsgAlertQryHandler(alerts, CLOCK);
        BilCaller own = new BilCaller("u-1", "a@df1", "df-1", true, false, false);
        BilCaller staff = new BilCaller("s-1", "ops@cm", null, false, true, false);
        BilCaller dp = new BilCaller("dp-1", "p@x", null, false, false, false);

        assertEquals(List.of("df-1"), qry.mine(own).map(UsgAlert::dfId).collectList().block());
        assertEquals(2, qry.forPeriod(staff, null).count().block());
        assertEquals(0, qry.forPeriod(staff, "2026-10").count().block());
        assertEquals(403, status(qry.mine(staff).then()));
        assertEquals(403, status(qry.forPeriod(own, null).then()));
        assertEquals(403, status(qry.mine(dp).then()));
        assertEquals(400, status(qry.forPeriod(staff, "2026-13").then()));
        assertEquals(400, status(qry.forPeriod(staff, "x").then()));
    }

    private static int status(Mono<?> call) {
        try {
            call.block();
            return 200;
        } catch (DomRuleViolation e) {
            return e.httpStatus();
        }
    }
}
