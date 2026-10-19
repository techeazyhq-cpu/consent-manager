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

package com.techeazyhq.cm.cnscmd.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.techeazyhq.cm.cnscmd.dom.PrpsDCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsDef;
import com.techeazyhq.cm.cnscmd.dom.PrpsDefRepoPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsNtc;
import com.techeazyhq.cm.cnscmd.dom.PrpsSensitivity;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Purposes held in memory for tests; {@link #orderUpdates()} defines and publishes ORDER_UPDATES version 1. */
final class InMemoryPrpsDefs implements PrpsDefRepoPort {

    static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    final Map<String, PrpsDef> byKey = new LinkedHashMap<>();
    final List<DomainEvt> events = new ArrayList<>();

    static PrpsVerContent orderUpdatesContent() {
        return new PrpsVerContent("Order updates", null,
                List.of(new PrpsDCat("EMAIL", "dpv-pd:EmailAddress", PrpsSensitivity.ORDINARY, true,
                                "Without your email we can't tell you when your order ships."),
                        new PrpsDCat("NAME", null, PrpsSensitivity.ORDINARY, false, null)),
                List.of(), List.of(), 365, 180, 365, Set.of(),
                List.of(new PrpsNtc("en", "Order updates", "We email you about your orders.")));
    }

    InMemoryPrpsDefs orderUpdates() {
        PrpsDef def = PrpsDef.create("df-1", "ORDER_UPDATES", orderUpdatesContent(), T0);
        def.publish(T0);
        save(def).block();
        return this;
    }

    @Override
    public Mono<PrpsDef> save(PrpsDef def) {
        events.addAll(def.pullEvts());
        byKey.put(def.dfId() + "/" + def.code(), def);
        return Mono.just(def);
    }

    @Override
    public Mono<PrpsDef> findByCode(String dfId, String code) {
        return Mono.justOrEmpty(byKey.get(dfId + "/" + code));
    }

    @Override
    public Flux<PrpsDef> listByDf(String dfId) {
        return Flux.fromIterable(byKey.values()).filter(d -> d.dfId().equals(dfId))
                .sort(Comparator.comparing(PrpsDef::code));
    }
}
