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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class OnDfActivatedEvtHandlerTest {

    private static final List<SubPln> PLANS = List.of(
            new SubPln("pln-free", "FREE", 5, 1_000), new SubPln("pln-std", "STANDARD", 50, 100_000));

    private final Map<String, DfSub> subs = new HashMap<>();
    private final OnDfActivatedEvtHandler handler = new OnDfActivatedEvtHandler(new InMemoryDfSubs(), new Catalogue());

    @Test
    void subscribesTheFiduciaryToThePlanChosenAtOnboarding() {
        handler.handle("df-1", "STANDARD").block();

        assertEquals("pln-std", subs.get("df-1").subPlnId());
    }

    @Test
    void anEventWithoutAPlanSubscribesToFree() {
        handler.handle("df-1", null).block();

        assertEquals("pln-free", subs.get("df-1").subPlnId());
    }

    @Test
    void aRedeliveredEventChangesNothing() {
        handler.handle("df-1", "STANDARD").block();
        handler.handle("df-1", "FREE").block();

        assertEquals("pln-std", subs.get("df-1").subPlnId());
    }

    @Test
    void aPlanThatNoLongerExistsFailsTheEventInsteadOfPickingAnother() {
        DomRuleViolation failure = assertThrows(DomRuleViolation.class, () -> handler.handle("df-1", "PLATINUM").block());

        assertEquals("sub-pln-missing", failure.problemType());
        assertTrue(subs.isEmpty());
    }

    private final class InMemoryDfSubs implements DfSubRepoPort {
        @Override
        public Mono<DfSub> save(DfSub dfSub) {
            subs.put(dfSub.id(), dfSub);
            return Mono.just(dfSub);
        }

        @Override
        public Mono<DfSub> findByDfId(String dfId) {
            return Mono.justOrEmpty(subs.get(dfId));
        }
    }

    private static final class Catalogue implements SubPlnRepoPort {
        @Override
        public Mono<SubPln> save(SubPln subPln) {
            return Mono.just(subPln);
        }

        @Override
        public Mono<SubPln> findById(String id) {
            return Mono.justOrEmpty(PLANS.stream().filter(p -> p.id().equals(id)).findFirst());
        }

        @Override
        public Mono<SubPln> findByName(String name) {
            return Mono.justOrEmpty(PLANS.stream().filter(p -> p.name().equals(name)).findFirst());
        }

        @Override
        public Flux<SubPln> findAll() {
            return Flux.fromIterable(PLANS);
        }
    }
}
