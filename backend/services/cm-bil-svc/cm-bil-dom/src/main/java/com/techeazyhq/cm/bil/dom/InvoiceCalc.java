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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What a fiduciary owes for a period (ADR-079): each stretch it was on a plan, charged that plan's monthly price in proportion
 * to how much of the period the stretch covers, to the second. A month on one plan is exactly the plan's price; a plan changed
 * on the 11th of a 30-day month charges 10/30 of one price and 20/30 of the other. Stretches outside the period, on a plan with
 * no price, or of no length are left out.
 */
public final class InvoiceCalc {

    private InvoiceCalc() {
    }

    /** A stretch on a plan, charged {@code monthlyMinor} for a whole period. */
    public record Segment(String planName, long monthlyMinor, Instant from, Instant to) { }

    /** The lines and totals of an invoice before it has an identity. */
    public record Draft(List<Invoice.Line> lines, long subtotalMinor, long taxMinor, long totalMinor) { }

    public static Draft calculate(Instant periodFrom, Instant periodTo, List<Segment> segments, int taxBps) {
        long periodSeconds = Duration.between(periodFrom, periodTo).getSeconds();
        List<Segment> ordered = new ArrayList<>(segments);
        ordered.sort(Comparator.comparing(Segment::from));
        List<Invoice.Line> lines = new ArrayList<>();
        long subtotal = 0;
        for (Segment s : ordered) {
            Instant from = s.from().isAfter(periodFrom) ? s.from() : periodFrom;
            Instant to = s.to() == null || s.to().isAfter(periodTo) ? periodTo : s.to();
            long seconds = Duration.between(from, to).getSeconds();
            if (seconds <= 0 || s.monthlyMinor() <= 0 || periodSeconds <= 0) {
                continue;
            }
            long amount = BigDecimal.valueOf(s.monthlyMinor()).multiply(BigDecimal.valueOf(seconds))
                    .divide(BigDecimal.valueOf(periodSeconds), 0, RoundingMode.HALF_UP).longValueExact();
            BigDecimal days = BigDecimal.valueOf(seconds).divide(BigDecimal.valueOf(86_400), 2, RoundingMode.HALF_UP);
            boolean whole = seconds == periodSeconds;
            String what = s.planName() + " plan, " + (whole ? "the whole month" : days.stripTrailingZeros().toPlainString() + " days");
            lines.add(new Invoice.Line(lines.size() + 1, what, s.planName(), from, to, days, amount));
            subtotal += amount;
        }
        long tax = BigDecimal.valueOf(subtotal).multiply(BigDecimal.valueOf(taxBps))
                .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_UP).longValueExact();
        return new Draft(lines, subtotal, tax, subtotal + tax);
    }
}
