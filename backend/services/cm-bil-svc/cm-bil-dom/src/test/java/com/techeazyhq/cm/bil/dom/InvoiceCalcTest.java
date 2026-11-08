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

package com.techeazyhq.cm.bil.dom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.bil.dom.InvoiceCalc.Segment;

/** What a fiduciary owes for a month: each stretch on a plan, in proportion to the period (ADR-079). */
class InvoiceCalcTest {

    private static final Instant NOV = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant DEC = Instant.parse("2026-12-01T00:00:00Z");

    @Test
    void aWholeMonthOnOnePlanIsExactlyThePlansPrice() {
        InvoiceCalc.Draft d = InvoiceCalc.calculate(NOV, DEC, List.of(new Segment("STANDARD", 499_900, NOV.minusSeconds(86_400 * 40L), null)), 0);

        assertEquals(1, d.lines().size());
        assertEquals(499_900, d.subtotalMinor());
        assertEquals(0, d.taxMinor());
        assertEquals(499_900, d.totalMinor());
        assertEquals("STANDARD plan, the whole month", d.lines().get(0).description());
    }

    @Test
    void aPlanChangedPartWayChargesEachStretchInProportion() {
        // 30-day month; STANDARD for the first 10 days, PREMIUM for the other 20
        Instant change = NOV.plusSeconds(10 * 86_400L);
        InvoiceCalc.Draft d = InvoiceCalc.calculate(NOV, DEC, List.of(
                new Segment("STANDARD", 300_000, NOV.minusSeconds(1000), change),
                new Segment("PREMIUM", 900_000, change, null)), 0);

        assertEquals(2, d.lines().size());
        assertEquals(100_000, d.lines().get(0).amountMinor(), "10/30 of 3000.00");
        assertEquals(600_000, d.lines().get(1).amountMinor(), "20/30 of 9000.00");
        assertEquals(700_000, d.totalMinor());
        assertEquals("STANDARD plan, 10 days", d.lines().get(0).description());
        assertEquals("10", d.lines().get(0).qtyDays().stripTrailingZeros().toPlainString());
    }

    @Test
    void aSubscriptionStartingMidMonthPaysFromItsStart() {
        Instant start = NOV.plusSeconds(15 * 86_400L);
        InvoiceCalc.Draft d = InvoiceCalc.calculate(NOV, DEC, List.of(new Segment("STANDARD", 300_000, start, null)), 0);

        assertEquals(150_000, d.totalMinor(), "15 of 30 days");
    }

    @Test
    void stretchesOutsideThePeriodOnAFreePlanOrOfNoLengthAreLeftOut() {
        InvoiceCalc.Draft d = InvoiceCalc.calculate(NOV, DEC, List.of(
                new Segment("STANDARD", 300_000, NOV.minusSeconds(86_400L * 60), NOV),            // ended as it began
                new Segment("FREE", 0, NOV, DEC),                                                  // no price
                new Segment("PREMIUM", 900_000, DEC, null),                                        // starts after
                new Segment("STANDARD", 300_000, NOV.plusSeconds(5), NOV.plusSeconds(5))), 0);     // no length

        assertTrue(d.lines().isEmpty());
        assertEquals(0, d.totalMinor());
    }

    @Test
    void taxIsOneRateOnTheSubtotalRoundedToTheMinorUnit() {
        InvoiceCalc.Draft d = InvoiceCalc.calculate(NOV, DEC, List.of(new Segment("STANDARD", 499_900, NOV, null)), 1800);

        assertEquals(89_982, d.taxMinor(), "18% of 4999.00 is 899.82");
        assertEquals(589_882, d.totalMinor());
    }

    @Test
    void roundingGoesToTheNearestMinorUnit() {
        // 1 minor unit a month for 10 of 30 days is a third of a unit: rounds down; 2 for 10 of 30 is two thirds: rounds up
        Instant change = NOV.plusSeconds(10 * 86_400L);
        assertEquals(0, InvoiceCalc.calculate(NOV, DEC, List.of(new Segment("X", 1, NOV, change)), 0).totalMinor());
        assertEquals(1, InvoiceCalc.calculate(NOV, DEC, List.of(new Segment("X", 2, NOV, change)), 0).totalMinor());
    }

    @Test
    void aCreditNoteIsTheInvoiceReversedLineForLine() {
        InvoiceCalc.Draft d = InvoiceCalc.calculate(NOV, DEC, List.of(new Segment("STANDARD", 499_900, NOV, null)), 1800);
        Invoice inv = new Invoice("i-1", "INV-2026-000001", Invoice.Kind.INVOICE, "df-1", NOV, DEC, "INR", d.subtotalMinor(), "GST", 1800,
                d.taxMinor(), d.totalMinor(), Invoice.Status.PAID, DEC, null, null, null, null, d.lines());

        Invoice note = inv.creditNote("c-1", "CN-2026-000001", "billed in error", DEC);

        assertEquals(Invoice.Kind.CREDIT_NOTE, note.kind());
        assertEquals(-inv.totalMinor(), note.totalMinor());
        assertEquals(-inv.taxMinor(), note.taxMinor());
        assertEquals(-inv.lines().get(0).amountMinor(), note.lines().get(0).amountMinor());
        assertEquals("i-1", note.creditsInvoiceId());
        assertEquals(inv.totalMinor() + note.totalMinor(), 0);
    }
}
