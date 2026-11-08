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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnsqry.dom.DfInsights;

/** The CSV export of the dashboard's figures (ADR-078). */
class InsightsCsvTest {

    private static final DfInsights INSIGHTS = DfInsights.of(Instant.parse("2026-10-19T00:00:00Z"), Instant.parse("2026-10-20T10:00:00Z"),
            List.of(new DfInsights.Purpose("ORDERS", new DfInsights.Outcomes(5, 3, 1, 0, 1), 3_600L),
                    new DfInsights.Purpose("NEWS", new DfInsights.Outcomes(2, 1, 0, 1, 0), null)),
            new DfInsights.Decisions(4, 600L, 7_200L, 4),
            List.of(new DfInsights.Day(LocalDate.parse("2026-10-19"), 4, 2, 1)), new DfInsights.Changes(0, 0, 0, 0));

    @Test
    void theDaysAreOneRowEachWithEmptyDaysIncluded() {
        assertEquals("date,raised,given,refused\n2026-10-19,4,2,1\n2026-10-20,0,0,0\n", InsightsCsv.days(INSIGHTS));
    }

    @Test
    void thePurposesAreMostRaisedFirstWithABlankMedianWhenNobodyDecided() {
        assertEquals("purpose,raised,given,refused,awaiting,not_notified,median_seconds_to_decide\n"
                + "ORDERS,5,3,1,0,1,3600\nNEWS,2,1,0,1,0,\n", InsightsCsv.purposes(INSIGHTS));
    }

    @Test
    void aCellIsQuotedWhenNeededAndDefusedWhenASpreadsheetWouldReadItAsAFormula() {
        assertEquals("plain", InsightsCsv.cell("plain"));
        assertEquals("\"a,b\"", InsightsCsv.cell("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", InsightsCsv.cell("say \"hi\""));
        assertEquals("'=SUM(A1)", InsightsCsv.cell("=SUM(A1)"));
        assertEquals("'-1", InsightsCsv.cell("-1"));
        assertEquals("", InsightsCsv.cell(null));
    }
}
