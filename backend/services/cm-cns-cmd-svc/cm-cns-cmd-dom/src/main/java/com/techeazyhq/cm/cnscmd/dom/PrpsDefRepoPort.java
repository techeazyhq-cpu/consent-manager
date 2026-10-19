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

/** Port for purpose definitions (ADR-031): implemented by {@code R2dbcPrpsDefRepoAdapter} in -infra. */
public interface PrpsDefRepoPort {

    /**
     * Stores the purpose and its versions, and its events in the outbox. A published version is written once and
     * never updated; only a draft changes.
     */
    Mono<PrpsDef> save(PrpsDef def);

    Mono<PrpsDef> findByCode(String dfId, String code);

    /** A fiduciary's purposes, by code. */
    Flux<PrpsDef> listByDf(String dfId);
}
