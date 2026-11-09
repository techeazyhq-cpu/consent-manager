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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnsqry.dom.DfInsights;
import com.techeazyhq.cm.cnsqry.dom.DfInsightsPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The dashboard's figures are the caller's own, over a window of 1 to 365 days, with every day present (ADR-049). */
class DfInsightsQryHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final List<String> asked = new ArrayList<>();
    private final List<java.time.ZoneId> zones = new ArrayList<>();
    private final DfInsightsPort port = new DfInsightsPort() {
        @Override
        public Flux<DfInsights.Purpose> byPurpose(String dfId, Instant from, Instant to) {
            asked.add(dfId + " " + Duration.between(from, to).toDays());
            return Flux.just(
                    new DfInsights.Purpose("NEWS", new DfInsights.Outcomes(2, 1, 0, 1, 0), 60L),
                    new DfInsights.Purpose("ORDERS", new DfInsights.Outcomes(5, 3, 1, 0, 1), 3_600L));
        }

        @Override
        public Mono<DfInsights.Decisions> decisions(String dfId, Instant from, Instant to) {
            return Mono.just(new DfInsights.Decisions(5, 600L, 7_200L, 5));
        }

        @Override
        public Flux<DfInsights.Day> byDay(String dfId, Instant from, Instant to, java.time.ZoneId zone) {
            zones.add(zone);
            return Flux.just(new DfInsights.Day(LocalDate.parse("2026-10-19"), 4, 2, 1));
        }

        @Override
        public Mono<DfInsights.Changes> changes(String dfId, Instant from, Instant to) {
            return Mono.just(new DfInsights.Changes(1, 2, 0, 1));
        }
    };
    private final DfInsightsQryHandler handler = new DfInsightsQryHandler(port, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void totalsThePurposesMostRaisedFirst() {
        DfInsights insights = handler.handle("df-1", 7).block();

        assertEquals(new DfInsights.Outcomes(7, 4, 1, 1, 1), insights.outcomes());
        assertEquals(80, insights.outcomes().grantRate());
        assertEquals(List.of("ORDERS", "NEWS"), insights.purposes().stream().map(DfInsights.Purpose::prpsCode).toList());
        assertEquals(new DfInsights.Changes(1, 2, 0, 1), insights.changes());
        assertEquals(List.of("df-1 7"), asked);
    }

    @Test
    void everyDayOfTheWindowIsThereEmptyOrNot() {
        List<DfInsights.Day> days = handler.handle("df-1", 7).block().days();

        assertEquals(8, days.size(), "13 Oct to 20 Oct, both partly in the window");
        assertEquals(LocalDate.parse("2026-10-13"), days.get(0).date());
        assertEquals(LocalDate.parse("2026-10-20"), days.get(7).date());
        assertEquals(4, days.get(6).raised());
        assertEquals(0, days.get(5).raised());
    }

    @Test
    void withoutComparisonThereIsNoPreviousWindowAndNothingExtraIsAsked() {
        DfInsights insights = handler.handle("df-1", 7, ZoneOffset.UTC, false).block();

        assertNull(insights.previous());
        assertEquals(List.of("df-1 7"), asked);
        assertEquals("UTC", insights.zone());
    }

    @Test
    void comparisonAddsTheTotalsOfTheWindowJustBefore() {
        DfInsights insights = handler.handle("df-1", 7, ZoneOffset.UTC, true).block();

        DfInsights.Previous before = insights.previous();
        assertEquals(Instant.parse("2026-10-06T10:00:00Z"), before.from());
        assertEquals(Instant.parse("2026-10-13T10:00:00Z"), before.to(), "it ends where the current window begins");
        assertEquals(insights.from(), before.to());
        assertEquals(new DfInsights.Outcomes(7, 4, 1, 1, 1), before.outcomes());
        assertEquals(List.of("df-1 7", "df-1 7"), asked, "both windows are 7 days");
    }

    @Test
    void daysAreCutInTheZoneAskedFor() {
        DfInsights insights = handler.handle("df-1", 1, java.time.ZoneId.of("Asia/Kolkata"), false).block();

        assertEquals("Asia/Kolkata", insights.zone());
        // 19 Oct 10:00 UTC to 20 Oct 10:00 UTC is 15:30 to 15:30 in India: still two dates
        assertEquals(List.of(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")),
                insights.days().stream().map(DfInsights.Day::date).toList());
        assertEquals(java.time.ZoneId.of("Asia/Kolkata"), zones.get(0));
    }

    private final List<String> windows = new ArrayList<>();

    private DfInsightsQryHandler recording() {
        DfInsightsPort recordingPort = new DfInsightsPort() {
            @Override
            public Flux<DfInsights.Purpose> byPurpose(String dfId, Instant from, Instant to) {
                windows.add(from + " .. " + to);
                return Flux.empty();
            }

            @Override
            public Mono<DfInsights.Decisions> decisions(String dfId, Instant from, Instant to) {
                return Mono.just(new DfInsights.Decisions(0, null, null, 0));
            }

            @Override
            public Flux<DfInsights.Day> byDay(String dfId, Instant from, Instant to, java.time.ZoneId zone) {
                return Flux.empty();
            }

            @Override
            public Mono<DfInsights.Changes> changes(String dfId, Instant from, Instant to) {
                return Mono.just(new DfInsights.Changes(0, 0, 0, 0));
            }
        };
        return new DfInsightsQryHandler(recordingPort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void thisMonthSoFarIsComparedWithTheSameNumberOfDaysAtTheStartOfLastMonth() {
        // 20 Oct 10:00 UTC: the month has run 19 days 10 hours
        DfInsights insights = recording().handleMonth("df-1", java.time.YearMonth.of(2026, 10), ZoneOffset.UTC, true).block();

        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), insights.from());
        assertEquals(NOW, insights.to(), "a month still going ends now");
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), insights.previous().from());
        assertEquals(Instant.parse("2026-09-20T10:00:00Z"), insights.previous().to(), "the same 19 days 10 hours of September");
    }

    @Test
    void aMonthThatHasEndedIsComparedWithTheWholeMonthBefore() {
        DfInsights insights = recording().handleMonth("df-1", java.time.YearMonth.of(2026, 9), ZoneOffset.UTC, true).block();

        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), insights.from());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), insights.to());
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), insights.previous().from());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), insights.previous().to(), "all of August, which is also 31 days against 30");
    }

    @Test
    void theMonthBeforeNeverRunsIntoTheMonthAsked() {
        // late in a long month the elapsed time is longer than the month before it: February is cut where March begins
        DfInsightsQryHandler late = new DfInsightsQryHandler(recording().port(), Clock.fixed(Instant.parse("2026-03-31T12:00:00Z"), ZoneOffset.UTC));
        DfInsights insights = late.handleMonth("df-1", java.time.YearMonth.of(2026, 3), ZoneOffset.UTC, true).block();

        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), insights.previous().to());
    }

    @Test
    void monthsAreCutInTheZoneAskedFor() {
        java.time.ZoneId india = java.time.ZoneId.of("Asia/Kolkata");
        DfInsights insights = recording().handleMonth("df-1", java.time.YearMonth.of(2026, 9), india, false).block();

        assertEquals(Instant.parse("2026-08-31T18:30:00Z"), insights.from(), "1 September in India begins 5 hours 30 minutes earlier in UTC");
        assertEquals(Instant.parse("2026-09-30T18:30:00Z"), insights.to());
    }

    @Test
    void aMonthInTheFutureOrTooFarBackIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> recording().handleMonth("df-1", java.time.YearMonth.of(2026, 11), ZoneOffset.UTC, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> recording().handleMonth("df-1", java.time.YearMonth.of(2024, 9), ZoneOffset.UTC, false));
        recording().handleMonth("df-1", java.time.YearMonth.of(2024, 10), ZoneOffset.UTC, false).block();
    }

    @Test
    void thisQuarterSoFarIsComparedWithTheSameStretchAtTheStartOfTheLastOne() {
        // 20 Oct 10:00 UTC: Q4 has run 19 days 10 hours
        DfInsights insights = recording().handleQuarter("df-1", 2026, 4, ZoneOffset.UTC, true).block();

        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), insights.from());
        assertEquals(NOW, insights.to(), "a quarter still going ends now");
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), insights.previous().from());
        assertEquals(Instant.parse("2026-07-20T10:00:00Z"), insights.previous().to());
    }

    @Test
    void aQuarterThatHasEndedIsComparedWithTheWholeQuarterBefore() {
        DfInsights insights = recording().handleQuarter("df-1", 2026, 3, ZoneOffset.UTC, true).block();

        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), insights.from());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), insights.to());
        assertEquals(Instant.parse("2026-04-01T00:00:00Z"), insights.previous().from());
        assertEquals(Instant.parse("2026-07-01T00:00:00Z"), insights.previous().to());
    }

    @Test
    void aQuarterBeforeTheNewYearComparesWithTheLastQuarterOfTheYearBefore() {
        DfInsights insights = recording().handleQuarter("df-1", 2026, 1, ZoneOffset.UTC, true).block();

        assertEquals(Instant.parse("2025-10-01T00:00:00Z"), insights.previous().from());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), insights.previous().to());
    }

    @Test
    void quartersAreCutInTheZoneAskedFor() {
        java.time.ZoneId india = java.time.ZoneId.of("Asia/Kolkata");
        DfInsights insights = recording().handleQuarter("df-1", 2026, 3, india, false).block();

        assertEquals(Instant.parse("2026-06-30T18:30:00Z"), insights.from());
        assertEquals(Instant.parse("2026-09-30T18:30:00Z"), insights.to());
    }

    @Test
    void aQuarterThatIsNotOneToFourInTheFutureOrTooFarBackIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> recording().handleQuarter("df-1", 2026, 5, ZoneOffset.UTC, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> recording().handleQuarter("df-1", 2026, 0, ZoneOffset.UTC, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> recording().handleQuarter("df-1", 2027, 1, ZoneOffset.UTC, false));
        // eight quarters back from Q4 2026 is Q4 2024; Q3 2024 is one too far
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> recording().handleQuarter("df-1", 2024, 3, ZoneOffset.UTC, false));
        recording().handleQuarter("df-1", 2024, 4, ZoneOffset.UTC, false).block();
    }

    @Test
    void theYearSoFarIsComparedWithTheSameStretchOfTheYearBefore() {
        // 20 Oct 10:00 UTC is 292 days 10 hours into 2026
        DfInsights insights = recording().handleYear("df-1", 2026, ZoneOffset.UTC, true).block();

        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), insights.from());
        assertEquals(NOW, insights.to());
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), insights.previous().from());
        assertEquals(Instant.parse("2025-10-20T10:00:00Z"), insights.previous().to(), "the same 292 days 10 hours of 2025");
    }

    @Test
    void aYearThatHasEndedIsComparedWithTheWholeYearBefore() {
        DfInsights insights = recording().handleYear("df-1", 2025, ZoneOffset.UTC, true).block();

        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), insights.from());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), insights.to());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"), insights.previous().from());
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"), insights.previous().to());
    }

    @Test
    void aYearInTheFutureOrTooFarBackIsRefused() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> recording().handleYear("df-1", 2027, ZoneOffset.UTC, false));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> recording().handleYear("df-1", 2023, ZoneOffset.UTC, false));
        recording().handleYear("df-1", 2024, ZoneOffset.UTC, false).block();
    }

    @Test
    void theWindowIsOneDayToAYear() {
        handler.handle("df-1", 0).block();
        handler.handle("df-1", 10_000).block();
        assertEquals(List.of("df-1 1", "df-1 365"), asked);
    }

    @Test
    void noRateWhenNobodyDecided() {
        assertNull(new DfInsights.Outcomes(3, 0, 0, 3, 0).grantRate());
    }
}
