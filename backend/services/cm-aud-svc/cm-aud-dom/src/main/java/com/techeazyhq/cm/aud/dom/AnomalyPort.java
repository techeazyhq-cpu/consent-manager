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
package com.techeazyhq.cm.aud.dom;

import java.time.Instant;
import java.util.UUID;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Port: the counts the detector reads from the ledger and the findings it keeps. Implemented by {@code R2dbcAnomalyAdapter}. */
public interface AnomalyPort {

    /** How many events of one type a fiduciary had in one hour. */
    record HourCount(String dfId, String evtType, Instant hour, long count) {
    }

    /** Events per fiduciary, type and hour from {@code from} (included) to {@code to} (excluded). Hours with none are absent. */
    Flux<HourCount> hourlyCounts(Instant from, Instant to);

    /** Keeps a finding; false when that fiduciary, signal and hour already has one (a run repeated). */
    Mono<Boolean> save(AnomalyFinding finding);

    /** Findings with the status ({@code null} for any), newest first. */
    Flux<AnomalyFinding> list(String status, int limit);

    /** Marks a finding acknowledged; false when there is no such open finding. */
    Mono<Boolean> acknowledge(UUID id, String by, Instant at);
}
