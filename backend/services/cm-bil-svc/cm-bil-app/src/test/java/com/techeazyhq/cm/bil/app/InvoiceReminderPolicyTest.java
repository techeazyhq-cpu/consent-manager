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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** When an unpaid invoice is reminded about (ADR-105): due after the payment terms, then at each step, the latest one reached. */
class InvoiceReminderPolicyTest {

    private static final Instant ISSUED = Instant.parse("2026-11-01T00:00:00Z");
    private final InvoiceReminderPolicy policy = new InvoiceReminderPolicy(14, List.of(3, 10, 20));

    private Instant afterDue(long days, long hours) {
        return policy.dueAt(ISSUED).plus(Duration.ofDays(days)).plus(Duration.ofHours(hours));
    }

    @Test
    void anInvoiceFallsDueAfterThePaymentTerms() {
        assertEquals(Instant.parse("2026-11-15T00:00:00Z"), policy.dueAt(ISSUED));
    }

    @Test
    void nothingIsSaidBeforeTheDueDateOrBeforeTheFirstStep() {
        assertNull(policy.stepReached(ISSUED, ISSUED));
        assertNull(policy.stepReached(ISSUED, afterDue(-1, 0)));
        assertNull(policy.stepReached(ISSUED, afterDue(0, 0)));
        assertNull(policy.stepReached(ISSUED, afterDue(2, 23)));
    }

    @Test
    void theStepIsReachedWhenItsDaysHavePassed() {
        assertEquals(3, policy.stepReached(ISSUED, afterDue(3, 0)));
        assertEquals(3, policy.stepReached(ISSUED, afterDue(9, 23)));
        assertEquals(10, policy.stepReached(ISSUED, afterDue(10, 0)));
        assertEquals(20, policy.stepReached(ISSUED, afterDue(20, 0)));
    }

    @Test
    void afterTheLastStepThereIsNoFurtherReminderAndTheLastStillCounts() {
        assertEquals(20, policy.stepReached(ISSUED, afterDue(400, 0)), "the last step stays the latest reached: it is raised once, not again");
    }

    @Test
    void aSweepThatWasDownSendsTheLatestStepNotEveryStepItMissed() {
        assertEquals(10, policy.stepReached(ISSUED, afterDue(12, 0)));
    }

    @Test
    void stepsAreSortedAndDeduplicated() {
        InvoiceReminderPolicy odd = new InvoiceReminderPolicy(0, List.of(20, 3, 3, 10));
        assertEquals(List.of(3, 10, 20), odd.stepsDays());
    }

    @Test
    void theFirstStepTellsTheSweepHowFarBackToLook() {
        Instant now = Instant.parse("2026-12-01T00:00:00Z");
        // issued 14 + 3 days ago or earlier
        assertEquals(Instant.parse("2026-11-14T00:00:00Z"), policy.firstStepIssuedBefore(now));
        assertNull(new InvoiceReminderPolicy(14, List.of()).firstStepIssuedBefore(now));
    }

    @Test
    void aNegativeTermOrAStepOfZeroDaysIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new InvoiceReminderPolicy(-1, List.of(3)));
        assertThrows(IllegalArgumentException.class, () -> new InvoiceReminderPolicy(14, List.of(0, 3)));
        assertThrows(IllegalArgumentException.class, () -> new InvoiceReminderPolicy(14, List.of(-5)));
    }
}
