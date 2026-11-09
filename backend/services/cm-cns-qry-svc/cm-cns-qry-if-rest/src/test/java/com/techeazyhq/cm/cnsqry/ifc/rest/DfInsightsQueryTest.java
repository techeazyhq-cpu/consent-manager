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

package com.techeazyhq.cm.cnsqry.ifc.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;

/** What the dashboard's query string may ask for: days, one calendar month, quarter or year (ADR-088, ADR-102); never two at once. */
class DfInsightsQueryTest {

    private static DfInsightsHandler.Query query(String... params) {
        MockServerRequest.Builder b = MockServerRequest.builder();
        for (int i = 0; i < params.length; i += 2) {
            b.queryParam(params[i], params[i + 1]);
        }
        return DfInsightsHandler.Query.of(b.build());
    }

    @Test
    void aQuarterIsYearAndNumber() {
        DfInsightsHandler.Query q = query("quarter", "2026-Q3");
        assertNotNull(q);
        assertArrayEquals(new int[] {2026, 3}, q.quarter());
        assertNull(q.year());
        assertNull(q.month());
    }

    @Test
    void aYearIsFourDigits() {
        assertEquals(2026, query("year", "2026").year());
    }

    @Test
    void aBadQuarterOrYearIsNotValid() {
        assertNull(query("quarter", "2026-Q5"));
        assertNull(query("quarter", "2026-q3"));
        assertNull(query("quarter", "2026-3"));
        assertNull(query("quarter", "26-Q3"));
        assertNull(query("year", "26"));
        assertNull(query("year", "twenty"));
        assertNull(query("year", "20266"));
    }

    @Test
    void askingForMoreThanOneCalendarPeriodIsNotValid() {
        assertNull(query("month", "2026-09", "quarter", "2026-Q3"));
        assertNull(query("month", "2026-09", "year", "2026"));
        assertNull(query("quarter", "2026-Q3", "year", "2026"));
    }

    @Test
    void theMonthAndDaysStillWork() {
        assertEquals(java.time.YearMonth.of(2026, 9), query("month", "2026-09").month());
        assertEquals(30, query().days());
    }
}
