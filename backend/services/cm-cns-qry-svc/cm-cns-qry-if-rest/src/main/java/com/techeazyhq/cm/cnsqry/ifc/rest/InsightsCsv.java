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

import com.techeazyhq.cm.cnsqry.dom.DfInsights;

/**
 * The dashboard's figures as CSV (ADR-078): counts and dates only, one header row, comma-separated, a line feed after each row.
 * A cell that could be read as a formula by a spreadsheet (starting with {@code = + - @}) is prefixed with an apostrophe, though
 * today no cell holds anything but a number, a date or a purpose code.
 */
final class InsightsCsv {

    private InsightsCsv() {
    }

    static String days(DfInsights i) {
        StringBuilder out = new StringBuilder("date,raised,given,refused\n");
        for (DfInsights.Day d : i.days()) {
            out.append(cell(d.date().toString())).append(',').append(d.raised()).append(',').append(d.given()).append(',')
                    .append(d.refused()).append('\n');
        }
        return out.toString();
    }

    static String purposes(DfInsights i) {
        StringBuilder out = new StringBuilder("purpose,raised,given,refused,awaiting,not_notified,median_seconds_to_decide\n");
        for (DfInsights.Purpose p : i.purposes()) {
            DfInsights.Outcomes o = p.outcomes();
            out.append(cell(p.prpsCode())).append(',').append(o.raised()).append(',').append(o.given()).append(',')
                    .append(o.refused()).append(',').append(o.awaiting()).append(',').append(o.notNotified()).append(',')
                    .append(p.medianSeconds() == null ? "" : p.medianSeconds()).append('\n');
        }
        return out.toString();
    }

    /** Quotes a cell that holds a comma, quote or line break, and defuses one that starts like a formula. */
    static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            v = '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
