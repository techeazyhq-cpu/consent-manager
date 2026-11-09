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

import java.time.Instant;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** What the reminder sweep reads and records (ADR-105): implemented by {@code R2dbcInvoiceReminderAdapter} in -infra. */
public interface InvoiceReminderPort {

    /** An issued invoice that nobody has paid or credited, and what a reminder says about it. */
    record Unpaid(String invoiceId, String number, String dfId, long totalMinor, String currency, Instant issuedAt) {
    }

    /** The invoices (not credit notes) still unpaid, with something to pay, issued at or before {@code issuedBefore}. */
    Flux<Unpaid> unpaidIssuedBefore(Instant issuedBefore);

    /**
     * Records that this step has been raised for the invoice unless it already was: true when it was new, so that of several replicas
     * sweeping at once exactly one raises it.
     */
    Mono<Boolean> addIfNew(String invoiceId, int stepDays, Instant raisedAt);
}
