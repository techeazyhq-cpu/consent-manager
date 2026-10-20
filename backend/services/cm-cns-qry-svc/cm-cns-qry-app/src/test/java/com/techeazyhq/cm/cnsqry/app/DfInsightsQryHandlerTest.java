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
        public Flux<DfInsights.Day> byDay(String dfId, Instant from, Instant to) {
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
