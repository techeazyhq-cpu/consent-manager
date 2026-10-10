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

package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Port: a DP's registered push devices. Implemented by {@code R2dbcPushDvcRepoAdapter}. */
public interface PushDvcRepoPort {

    /**
     * Registers {@code token} for {@code dpId}, or refreshes it if already registered — moving it to {@code dpId} if
     * another DP had it — then drops that DP's oldest devices beyond {@link PushDvc#MAX_PER_DP}.
     */
    Mono<PushDvc> upsert(String dpId, String token, PushPltf platform);

    Flux<PushDvc> findByDpId(String dpId);

    /** @return whether a device {@code id} belonging to {@code dpId} existed */
    Mono<Boolean> delete(String dpId, String id);
}
