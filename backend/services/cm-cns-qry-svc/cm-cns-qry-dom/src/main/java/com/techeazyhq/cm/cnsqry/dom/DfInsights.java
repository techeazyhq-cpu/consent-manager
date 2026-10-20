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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a fiduciary's consent requests went over a window (ADR-048): the outcome of each request raised in it, how
 * long people took to decide, a day-by-day series, and the changes people made in it to consents they had given.
 * Counts and durations only; no request or principal is identified.
 *
 * @param outcomes  of the requests raised in the window, by their first decision
 * @param decisions how long the decided ones took, from request to decision
 * @param purposes  the same outcomes, purpose by purpose, most raised first
 * @param days      every day of the window (UTC): raised that day, and given or refused that day
 * @param changes   withdrawals, narrowings, renewals and lapses in the window, of any consent
 */
public record DfInsights(Instant from, Instant to, Outcomes outcomes, Decisions decisions, List<Purpose> purposes,
                         List<Day> days, Changes changes) {

    /** Outcomes of requests: given, refused, still awaiting, or never reached the person. */
    public record Outcomes(long raised, long given, long refused, long awaiting, long notNotified) {

        /** Of those who decided, the share who gave consent, in whole percent; null when nobody decided. */
        public Integer grantRate() {
            long decided = given + refused;
            return decided == 0 ? null : (int) Math.round(100.0 * given / decided);
        }

        Outcomes plus(Outcomes o) {
            return new Outcomes(raised + o.raised, given + o.given, refused + o.refused, awaiting + o.awaiting,
                    notNotified + o.notNotified);
        }
    }

    /** Time to decide, in seconds; the median and 90th percentile are null when nobody decided. */
    public record Decisions(long decided, Long medianSeconds, Long p90Seconds, long withinADay) {
    }

    public record Purpose(String prpsCode, Outcomes outcomes, Long medianSeconds) {
    }

    public record Day(LocalDate date, long raised, long given, long refused) {
    }

    public record Changes(long withdrawn, long narrowed, long renewed, long lapsed) {
    }

    /** Assembles the parts the read model gives, totalling the purposes and filling days with nothing in them. */
    public static DfInsights of(Instant from, Instant to, List<Purpose> purposes, Decisions decisions, List<Day> dayRows,
                                Changes changes) {
        Outcomes total = new Outcomes(0, 0, 0, 0, 0);
        for (Purpose p : purposes) {
            total = total.plus(p.outcomes());
        }
        Map<LocalDate, Day> byDate = new LinkedHashMap<>();
        for (Day d : dayRows) {
            byDate.put(d.date(), d);
        }
        List<Day> days = new ArrayList<>();
        LocalDate last = to.atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate d = from.atZone(ZoneOffset.UTC).toLocalDate(); !d.isAfter(last); d = d.plusDays(1)) {
            days.add(byDate.getOrDefault(d, new Day(d, 0, 0, 0)));
        }
        List<Purpose> sorted = purposes.stream()
                .sorted((a, b) -> Long.compare(b.outcomes().raised(), a.outcomes().raised()))
                .toList();
        return new DfInsights(from, to, total, decisions, sorted, days, changes);
    }
}
