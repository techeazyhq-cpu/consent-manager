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

package com.techeazyhq.cm.dsr.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DpReqRepoPort {

    /**
     * Inserts or updates. Refuses a second open request for the same consent and right with
     * {@link OpenDpReqExistsException}.
     */
    Mono<DpReq> save(DpReq req);

    Mono<DpReq> findById(String id);

    /** A principal's requests, newest first. */
    Flux<DpReq> findByDpId(String dpId, int limit);

    /** The principal's open requests, for withdrawing when they close their account. */
    Flux<DpReq> findOpenByDpId(String dpId);

    /**
     * Erases the words of every request the principal made and every answer to them (ADR-059): what was asked, of
     * whom, when and how it ended stay, as the record of a right exercised. Returns how many requests it touched.
     */
    Mono<Long> eraseTextOf(String dpId);

    /** How many requests the principal has open, against {@code DpReqPolicy.MAX_OPEN}. */
    Mono<Long> countOpenByDpId(String dpId);

    /** A fiduciary's relayed requests in {@code sts} (all when null): open ones by deadline, closed ones newest first. */
    Flux<DpReq> findByDfId(String dfId, DpReq.Sts sts, int limit);

    /** Grievances about the platform in {@code sts} (all when null), ordered as {@link #findByDfId}. */
    Flux<DpReq> findGrievances(DpReq.Sts sts, int limit);
}
