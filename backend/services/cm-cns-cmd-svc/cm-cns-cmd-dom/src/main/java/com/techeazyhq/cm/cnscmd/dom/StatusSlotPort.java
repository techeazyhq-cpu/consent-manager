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

/**
 * Port for the places consents have in their fiduciary's status list (ADR-077): implemented by
 * {@code R2dbcStatusSlotAdapter} in -infra.
 */
public interface StatusSlotPort {

    /** Size of every list, in bits: large enough that an index says nothing about whose consent it is. */
    int LIST_SIZE = 262_144;

    /** The consent's index in its fiduciary's list, chosen at random and kept; the same index every time it is asked. */
    Mono<Integer> slotFor(String dfId, String cnsArtId);

    /** Every index the fiduciary has used, and whether its consent has ended (withdrawn, lapsed or expired). */
    Flux<Entry> entries(String dfId);

    record Entry(int idx, boolean ended) { }
}
