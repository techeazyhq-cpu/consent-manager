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
import java.time.Instant;
import java.util.List;

/**
 * An invoice or a credit note (ADR-079): what a fiduciary owes for a calendar month (UTC) of its plan, or the reversal of one.
 * Amounts are in minor units (paise, cents) of {@code currency}, tax is one rate shown as its own line, and once issued
 * nothing about it changes except whether it was paid or credited. A credit note carries the same lines negated.
 *
 * @param number  sequential within its kind and year, for example {@code INV-2026-000012}
 * @param taxBps  basis points: 1800 is 18%
 */
public record Invoice(String id, String number, Kind kind, String dfId, Instant periodFrom, Instant periodTo, String currency,
                      long subtotalMinor, String taxLabel, int taxBps, long taxMinor, long totalMinor, Status sts,
                      Instant issuedAt, Instant paidAt, String paidNote, String creditsInvoiceId, String reason,
                      List<Line> lines) {

    public enum Kind { INVOICE, CREDIT_NOTE }

    public enum Status { ISSUED, PAID, CREDITED }

    /** One stretch of one plan within the period: {@code qtyDays} of it, charged {@code amountMinor}. */
    public record Line(int lineNo, String description, String planName, Instant from, Instant to, BigDecimal qtyDays,
                       long amountMinor) {
        Line negated(int no) {
            return new Line(no, description, planName, from, to, qtyDays.negate(), -amountMinor);
        }
    }

    /** The credit note that reverses this invoice in full. */
    public Invoice creditNote(String id, String number, String reason, Instant now) {
        List<Line> negated = new java.util.ArrayList<>();
        for (Line l : lines) {
            negated.add(l.negated(l.lineNo()));
        }
        return new Invoice(id, number, Kind.CREDIT_NOTE, dfId, periodFrom, periodTo, currency, -subtotalMinor, taxLabel, taxBps,
                -taxMinor, -totalMinor, Status.ISSUED, now, null, null, this.id, reason, negated);
    }
}
