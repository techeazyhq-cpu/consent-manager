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

package com.techeazyhq.cm.cnsqry.app;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.techeazyhq.cm.cnsqry.dom.DfInsights;
import com.techeazyhq.cm.cnsqry.dom.DfInsightsPort;

import reactor.core.publisher.Mono;

/**
 * Backs the DF Console dashboard (ADR-048): a fiduciary's own outcomes and times to decide over the last
 * {@code days}, from 1 to {@link #MAX_DAYS}. The caller's fiduciary comes from its token, never the request.
 */
public class DfInsightsQryHandler {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 365;

    private final DfInsightsPort port;
    private final Clock clock;

    public DfInsightsQryHandler(DfInsightsPort port, Clock clock) {
        this.port = port;
        this.clock = clock;
    }

    public Mono<DfInsights> handle(String dfId, int days) {
        int window = Math.min(Math.max(days, 1), MAX_DAYS);
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(window));
        return Mono.zip(port.byPurpose(dfId, from, to).collectList(), port.decisions(dfId, from, to),
                        port.byDay(dfId, from, to).collectList(), port.changes(dfId, from, to))
                .map(t -> DfInsights.of(from, to, t.getT1(), t.getT2(), t.getT3(), t.getT4()));
    }
}
