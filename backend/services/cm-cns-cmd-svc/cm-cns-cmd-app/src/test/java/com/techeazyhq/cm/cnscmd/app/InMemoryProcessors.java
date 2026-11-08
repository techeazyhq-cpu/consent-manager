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

import java.util.LinkedHashMap;
import java.util.Map;

import com.techeazyhq.cm.cnscmd.dom.Processor;
import com.techeazyhq.cm.cnscmd.dom.ProcessorRepoPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** A register of processors held in memory for tests. */
final class InMemoryProcessors implements ProcessorRepoPort {

    final Map<String, Processor> byId = new LinkedHashMap<>();

    @Override
    public Mono<Processor> save(Processor processor) {
        return Mono.fromRunnable(() -> byId.put(processor.id(), processor)).thenReturn(processor);
    }

    @Override
    public Mono<Processor> find(String dfId, String id) {
        return Mono.justOrEmpty(byId.get(id)).filter(p -> p.dfId().equals(dfId));
    }

    @Override
    public Mono<Processor> findByName(String dfId, String name) {
        return Flux.fromIterable(byId.values())
                .filter(p -> p.dfId().equals(dfId) && Processor.key(p.name()).equals(Processor.key(name))).next();
    }

    @Override
    public Flux<Processor> list(String dfId) {
        return Flux.fromIterable(byId.values()).filter(p -> p.dfId().equals(dfId))
                .sort((a, b) -> Processor.key(a.name()).compareTo(Processor.key(b.name())));
    }
}
