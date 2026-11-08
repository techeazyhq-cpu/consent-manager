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
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.techeazyhq.cm.cnsqry.dom.DfInsights;
import com.techeazyhq.cm.cnsqry.dom.DfInsightsPort;

import reactor.core.publisher.Mono;

/**
 * Backs the DF Console dashboard (ADR-049): a fiduciary's own outcomes and times to decide over the last
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
        return handle(dfId, days, ZoneOffset.UTC, false);
    }

    /**
     * The figures for the last {@code days}, with the days cut in {@code zone}; with {@code compare}, also the totals for
     * the window of equal length just before (ADR-049's "Not yet", ADR-078).
     */
    public Mono<DfInsights> handle(String dfId, int days, ZoneId zone, boolean compare) {
        int window = Math.min(Math.max(days, 1), MAX_DAYS);
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(window));
        Mono<DfInsights> current = Mono.zip(port.byPurpose(dfId, from, to).collectList(), port.decisions(dfId, from, to),
                        port.byDay(dfId, from, to, zone).collectList(), port.changes(dfId, from, to))
                .map(t -> DfInsights.of(from, to, t.getT1(), t.getT2(), t.getT3(), t.getT4(), zone));
        if (!compare) {
            return current;
        }
        Instant before = from.minus(Duration.ofDays(window));
        Mono<DfInsights.Previous> previous = Mono.zip(port.byPurpose(dfId, before, from).collectList(),
                        port.decisions(dfId, before, from), port.changes(dfId, before, from))
                .map(t -> {
                    DfInsights totals = DfInsights.of(before, from, t.getT1(), t.getT2(), java.util.List.of(), t.getT3(), zone);
                    return new DfInsights.Previous(before, from, totals.outcomes(), t.getT2(), t.getT3());
                });
        return Mono.zip(current, previous).map(t -> t.getT1().withPrevious(t.getT2()));
    }
}
