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

package com.techeazyhq.cm.cnscmd.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Port for a fiduciary's register of processors (ADR-075): implemented by {@code R2dbcProcessorRepoAdapter} in -infra. */
public interface ProcessorRepoPort {

    /** Inserts or updates; the name is unique per fiduciary, ignoring case. */
    Mono<Processor> save(Processor processor);

    Mono<Processor> find(String dfId, String id);

    Mono<Processor> findByName(String dfId, String name);

    /** A fiduciary's processors, by name. */
    Flux<Processor> list(String dfId);
}
