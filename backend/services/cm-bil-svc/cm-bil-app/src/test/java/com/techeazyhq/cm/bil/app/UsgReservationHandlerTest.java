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

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.bil.app.UsgReservationHandler.Reservation;
import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubFactory;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.DfSubSts;
import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.bil.dom.UsgPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class UsgReservationHandlerTest {

    private static final SubPln FREE = new SubPln("pln-free", "FREE", 5, 1_000);

    private final Map<String, DfSub> subs = new ConcurrentHashMap<>();
    private final AtomicLong usage = new AtomicLong();

    private final UsgPort usg = new UsgPort() {
        @Override
        public Mono<Long> currentPeriodUsage(String dfId) {
            return Mono.just(usage.get());
        }

        @Override
        public Mono<Long> add(String dfId, long count) {
            return Mono.just(usage.addAndGet(count));
        }
    };

    private final DfSubRepoPort dfSubs = new DfSubRepoPort() {
        @Override
        public Mono<DfSub> save(DfSub dfSub) {
            subs.put(dfSub.id(), dfSub);
            return Mono.just(dfSub);
        }

        @Override
        public Mono<DfSub> findByDfId(String dfId) {
            return Mono.justOrEmpty(subs.get(dfId));
        }
    };

    private final SubPlnRepoPort plans = new SubPlnRepoPort() {
        @Override
        public Mono<SubPln> save(SubPln subPln) {
            return Mono.just(subPln);
        }

        @Override
        public Mono<SubPln> findById(String id) {
            return Mono.justOrEmpty(FREE.id().equals(id) ? FREE : null);
        }

        @Override
        public Mono<SubPln> findByName(String name) {
            return Mono.empty();
        }

        @Override
        public Flux<SubPln> findAll() {
            return Flux.just(FREE);
        }
    };

    private final UsgReservationHandler handler = new UsgReservationHandler(dfSubs, plans, usg);

    private void subscribed(DfSubSts sts) {
        subs.put("df-1", DfSubFactory.rehydrate("df-1", FREE.id(), sts, Instant.now(), Instant.now().plusSeconds(86_400)));
    }

    @Test
    void reservesWhatFits() {
        subscribed(DfSubSts.ACTIVE);
        usage.set(200);

        assertEquals(new Reservation(true, 300), handler.reserve("df-1", 500).block());
        assertEquals(700, usage.get());
    }

    @Test
    void refusesAllOfABatchThatDoesNotFitAndTakesNothing() {
        subscribed(DfSubSts.ACTIVE);
        usage.set(900);

        assertEquals(new Reservation(false, 100), handler.reserve("df-1", 101).block());
        assertEquals(900, usage.get(), "the overshoot is undone");
    }

    @Test
    void releasesWhatABatchDidNotSend() {
        subscribed(DfSubSts.ACTIVE);
        handler.reserve("df-1", 50).block();

        handler.release("df-1", 20).block();

        assertEquals(30, usage.get());
    }

    @Test
    void aSuspendedSubscriptionReservesNothing() {
        subscribed(DfSubSts.SUSPENDED);

        DomRuleViolation refused = assertThrows(DomRuleViolation.class, () -> handler.reserve("df-1", 1).block());
        assertEquals(402, refused.httpStatus());
        assertEquals(0, usage.get());
    }

    @Test
    void aCountOutsideTheLimitsIsA400() {
        subscribed(DfSubSts.ACTIVE);

        assertEquals(400, assertThrows(DomRuleViolation.class, () -> handler.reserve("df-1", 0).block()).httpStatus());
        assertEquals(400, assertThrows(DomRuleViolation.class,
                () -> handler.reserve("df-1", UsgReservationHandler.MAX_COUNT + 1).block()).httpStatus());
    }
}
