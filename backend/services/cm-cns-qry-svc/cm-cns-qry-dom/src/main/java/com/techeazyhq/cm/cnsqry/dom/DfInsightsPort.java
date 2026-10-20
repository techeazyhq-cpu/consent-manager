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

package com.techeazyhq.cm.cnsqry.dom;

import java.time.Instant;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The aggregates behind {@link DfInsights}, for one fiduciary and the window {@code [from, to)} (ADR-048). */
public interface DfInsightsPort {

    /** Outcomes and median time to decide of requests raised in the window, by purpose. */
    Flux<DfInsights.Purpose> byPurpose(String dfId, Instant from, Instant to);

    /** Time to decide over every request raised in the window and decided. */
    Mono<DfInsights.Decisions> decisions(String dfId, Instant from, Instant to);

    /** Days with something in them (UTC): requests raised, and those raised in the window given or refused. */
    Flux<DfInsights.Day> byDay(String dfId, Instant from, Instant to);

    /** Changes people made in the window to any of the fiduciary's consents. */
    Mono<DfInsights.Changes> changes(String dfId, Instant from, Instant to);
}
