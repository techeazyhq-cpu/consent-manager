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

    /** The read model this answers from (for tests that want another clock over the same data). */
    public DfInsightsPort port() {
        return port;
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
        return figures(dfId, from, to, from.minus(Duration.ofDays(window)), from, zone, compare);
    }

    /** How far back a calendar month may be asked for. */
    public static final int MAX_MONTHS_BACK = 24;

    /**
     * The figures for one calendar month in {@code zone} (ADR-088): all of a month that has ended, and the month so far for this one.
     * With {@code compare}, the month before: the whole of it when this month has ended, and only the same number of days from its
     * start when this month is still going, so "this month" is never set against a month with more days in it.
     *
     * @throws IllegalArgumentException for a month in the future, or further back than {@link #MAX_MONTHS_BACK}
     */
    public Mono<DfInsights> handleMonth(String dfId, java.time.YearMonth month, ZoneId zone, boolean compare) {
        java.time.YearMonth thisMonth = java.time.YearMonth.from(clock.instant().atZone(zone));
        if (month.isAfter(thisMonth) || month.isBefore(thisMonth.minusMonths(MAX_MONTHS_BACK))) {
            throw new IllegalArgumentException("the month is from the last " + MAX_MONTHS_BACK + " months up to this one");
        }
        return calendarPeriod(dfId, month.atDay(1), month.plusMonths(1).atDay(1), month.minusMonths(1).atDay(1), zone, compare);
    }

    /** How far back a calendar quarter, and a calendar year, may be asked for (ADR-102). */
    public static final int MAX_QUARTERS_BACK = 8;
    public static final int MAX_YEARS_BACK = 2;

    /**
     * The figures for one calendar quarter (1 to 4) in {@code zone} (ADR-102), compared like a month: against all of the quarter before when
     * this one has ended, and against the same stretch of the start of the quarter before when it is still going.
     *
     * @throws IllegalArgumentException for a quarter that is not 1 to 4, in the future, or further back than {@link #MAX_QUARTERS_BACK}
     */
    public Mono<DfInsights> handleQuarter(String dfId, int year, int quarter, ZoneId zone, boolean compare) {
        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("a quarter is 1 to 4");
        }
        java.time.LocalDate today = clock.instant().atZone(zone).toLocalDate();
        java.time.LocalDate start = java.time.LocalDate.of(year, (quarter - 1) * 3 + 1, 1);
        java.time.LocalDate thisQuarter = java.time.LocalDate.of(today.getYear(), ((today.getMonthValue() - 1) / 3) * 3 + 1, 1);
        if (start.isAfter(thisQuarter) || start.isBefore(thisQuarter.minusMonths(3L * MAX_QUARTERS_BACK))) {
            throw new IllegalArgumentException("the quarter is from the last " + MAX_QUARTERS_BACK + " quarters up to this one");
        }
        return calendarPeriod(dfId, start, start.plusMonths(3), start.minusMonths(3), zone, compare);
    }

    /**
     * The figures for one calendar year in {@code zone} (ADR-102): all of a year that has ended and the year so far for this one, compared with the
     * year before in the same way.
     *
     * @throws IllegalArgumentException for a year in the future or further back than {@link #MAX_YEARS_BACK}
     */
    public Mono<DfInsights> handleYear(String dfId, int year, ZoneId zone, boolean compare) {
        int thisYear = clock.instant().atZone(zone).getYear();
        if (year > thisYear || year < thisYear - MAX_YEARS_BACK) {
            throw new IllegalArgumentException("the year is from the last " + MAX_YEARS_BACK + " years up to this one");
        }
        java.time.LocalDate start = java.time.LocalDate.of(year, 1, 1);
        return calendarPeriod(dfId, start, start.plusYears(1), start.minusYears(1), zone, compare);
    }

    /**
     * One calendar period, from its first local day to where the next begins: all of it when it has ended, and so far when it is still
     * going. Compared with the period before, which starts at {@code beforeStart}: all of it when this one has ended, and only the same
     * stretch of its start when this one is still going, never past where this one begins.
     */
    private Mono<DfInsights> calendarPeriod(String dfId, java.time.LocalDate start, java.time.LocalDate next,
                                            java.time.LocalDate beforeStart, ZoneId zone, boolean compare) {
        Instant from = start.atStartOfDay(zone).toInstant();
        Instant end = next.atStartOfDay(zone).toInstant();
        Instant to = end.isAfter(clock.instant()) ? clock.instant() : end;
        Instant beforeFrom = beforeStart.atStartOfDay(zone).toInstant();
        Instant beforeTo = end.isAfter(clock.instant()) ? beforeFrom.plus(Duration.between(from, to)) : from;
        if (beforeTo.isAfter(from)) {
            beforeTo = from; // the period before ends where this one begins, however long this one has run
        }
        return figures(dfId, from, to, beforeFrom, beforeTo, zone, compare);
    }

    private Mono<DfInsights> figures(String dfId, Instant from, Instant to, Instant before, Instant beforeTo, ZoneId zone,
                                     boolean compare) {
        Mono<DfInsights> current = Mono.zip(port.byPurpose(dfId, from, to).collectList(), port.decisions(dfId, from, to),
                        port.byDay(dfId, from, to, zone).collectList(), port.changes(dfId, from, to))
                .map(t -> DfInsights.of(from, to, t.getT1(), t.getT2(), t.getT3(), t.getT4(), zone));
        if (!compare) {
            return current;
        }
        Mono<DfInsights.Previous> previous = Mono.zip(port.byPurpose(dfId, before, beforeTo).collectList(),
                        port.decisions(dfId, before, beforeTo), port.changes(dfId, before, beforeTo))
                .map(t -> {
                    DfInsights totals = DfInsights.of(before, beforeTo, t.getT1(), t.getT2(), java.util.List.of(), t.getT3(), zone);
                    return new DfInsights.Previous(before, beforeTo, totals.outcomes(), t.getT2(), t.getT3(), totals.purposes());
                });
        return Mono.zip(current, previous).map(t -> t.getT1().withPrevious(t.getT2()));
    }
}
