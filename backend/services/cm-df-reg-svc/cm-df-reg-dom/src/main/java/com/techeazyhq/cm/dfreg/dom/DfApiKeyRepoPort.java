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

package com.techeazyhq.cm.dfreg.dom;

import java.time.Instant;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DfApiKeyRepoPort {

    Mono<DfApiKey> save(DfApiKey key);

    Mono<DfApiKey> findById(String id);

    Mono<DfApiKey> findByHash(String keyHash);

    /** A fiduciary's keys, newest first, revoked ones too. */
    Flux<DfApiKey> findByDfId(String dfId);

    /** Notes the key was used, at most once a minute, so busy keys don't write on every call. */
    Mono<Void> touch(String id, Instant at);
}
