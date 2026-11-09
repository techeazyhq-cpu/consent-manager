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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * When an unpaid invoice is reminded about (ADR-105): it falls due {@code paymentTermsDays} after it is issued, and is reminded about at
 * each of {@code stepsDays} after that. Only the latest step reached is raised, so a sweep that was down for a while sends one reminder,
 * not several at once.
 */
public record InvoiceReminderPolicy(int paymentTermsDays, List<Integer> stepsDays) {

    public InvoiceReminderPolicy {
        if (paymentTermsDays < 0) {
            throw new IllegalArgumentException("the payment terms cannot be negative");
        }
        stepsDays = stepsDays.stream().distinct().sorted().toList();
        if (stepsDays.stream().anyMatch(d -> d <= 0)) {
            throw new IllegalArgumentException("a reminder step is a number of days after the due date, 1 or more");
        }
    }

    /** When the invoice falls due. */
    public Instant dueAt(Instant issuedAt) {
        return issuedAt.plus(Duration.ofDays(paymentTermsDays));
    }

    /** The latest step reached at {@code now}, or null when there is none yet (or no steps). */
    public Integer stepReached(Instant issuedAt, Instant now) {
        long overdueDays = Duration.between(dueAt(issuedAt), now).toDays();
        Integer reached = null;
        for (int step : stepsDays) {
            if (step <= overdueDays) {
                reached = step;
            }
        }
        return reached;
    }

    /** The earliest an invoice can have been issued and already be at the first step; null when there are no steps. */
    public Instant firstStepIssuedBefore(Instant now) {
        return stepsDays.isEmpty() ? null : now.minus(Duration.ofDays((long) paymentTermsDays + stepsDays.get(0)));
    }
}
