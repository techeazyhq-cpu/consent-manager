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

package com.techeazyhq.cm.bil.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsgAlertRepoPort {

    /**
     * Records the alert unless this fiduciary already has one for the month and threshold: true when it was new, so
     * that of several replicas sweeping at once exactly one raises it.
     */
    Mono<Boolean> addIfNew(UsgAlert alert);

    /** A fiduciary's alerts, newest first. */
    Flux<UsgAlert> findByDf(String dfId, int limit);

    /** Every fiduciary's alerts for {@code period} ({@code yyyy-MM}), newest first. */
    Flux<UsgAlert> findByPeriod(String period);
}
