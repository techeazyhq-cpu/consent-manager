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

package com.techeazyhq.cm.bil.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.bil.dom.Invoice;

/** Invoices as accounts read them: a CSV row each, and a printable page (ADR-079). */
class InvoiceExportTest {

    private static final Instant NOV = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant DEC = Instant.parse("2026-12-01T00:00:00Z");

    private static Invoice invoice(String planName) {
        Invoice.Line line = new Invoice.Line(1, planName + " plan, the whole month", planName, NOV, DEC, new BigDecimal("30.00"), 499_900);
        return new Invoice("i-1", "INV-2026-000007", Invoice.Kind.INVOICE, "df-1", NOV, DEC, "INR", 499_900, "GST", 1800, 89_982, 589_882,
                Invoice.Status.ISSUED, DEC, null, null, null, null, List.of(line));
    }

    @Test
    void moneyIsMajorUnitsWithTwoDecimalsAndKeepsItsSign() {
        assertEquals("12.50", InvoiceExport.money(1250));
        assertEquals("0.05", InvoiceExport.money(5));
        assertEquals("0.00", InvoiceExport.money(0));
        assertEquals("-0.50", InvoiceExport.money(-50));
        assertEquals("18.00", InvoiceExport.money(1800));
    }

    @Test
    void theCsvHasAHeaderAndARowPerInvoice() {
        String[] rows = InvoiceExport.csv(List.of(invoice("STANDARD"))).split("\n");

        assertEquals(2, rows.length);
        assertTrue(rows[0].startsWith("number,kind,fiduciary,period_from"));
        assertEquals("INV-2026-000007,INVOICE,df-1,2026-11-01T00:00:00Z,2026-12-01T00:00:00Z,INR,4999.00,GST,18.00,899.82,5898.82,ISSUED,"
                + "2026-12-01T00:00:00Z,,", rows[1]);
    }

    @Test
    void aCellThatLooksLikeAFormulaIsDefused() {
        assertEquals("'=1+1", InvoiceExport.cell("=1+1"));
        assertEquals("\"a,b\"", InvoiceExport.cell("a,b"));
    }

    @Test
    void thePageEscapesWhatItWritesAndShowsTheTotals() {
        String page = InvoiceExport.html(invoice("<b>x</b>"), "Acme & Co");

        assertTrue(page.contains("Invoice INV-2026-000007"));
        assertTrue(page.contains("Acme &amp; Co"));
        assertTrue(page.contains("&lt;b&gt;x&lt;/b&gt; plan"));
        assertFalse(page.contains("<b>x</b>"));
        assertTrue(page.contains("5898.82"));
        assertTrue(page.contains("GST 18.00%"));
        assertTrue(page.contains("2026-11-01 to 2026-11-30"), "the last day of the period, not the first of the next month");
    }
}
