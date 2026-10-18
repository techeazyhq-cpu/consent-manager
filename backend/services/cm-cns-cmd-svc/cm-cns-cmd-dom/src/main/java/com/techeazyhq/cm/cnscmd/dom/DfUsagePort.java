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

import reactor.core.publisher.Mono;

/** A fiduciary's monthly allowance of consent requests, kept by cm-bil-svc (ADR-036). */
public interface DfUsagePort {

    /** {@code reserved}: whether all {@code count} fit; {@code available}: what is left this month either way. */
    record Reservation(boolean reserved, long available) {
    }

    /** Reserves {@code count} requests at once, or none if they don't all fit. */
    Mono<Reservation> reserve(String dfId, long count);

    /** Gives back reserved requests that weren't sent. */
    Mono<Void> release(String dfId, long count);

    /** What is left this month, for a preview; nothing is reserved. */
    Mono<Long> available(String dfId);
}
