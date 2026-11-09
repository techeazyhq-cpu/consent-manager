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

/** Port for plan prices, plan history and invoices (ADR-079): implemented by {@code R2dbcInvoiceRepoAdapter} in -infra. */
public interface InvoiceRepoPort {

    /** A plan and its monthly price. */
    record PlanPrice(String planName, long monthlyMinor) { }

    Flux<PlanPrice> prices();

    /** Sets a plan's monthly price; empty when there is no such plan. */
    Mono<PlanPrice> setPrice(String planName, long monthlyMinor, Instant now);

    /** The fiduciaries that were on a plan at any time in the period. */
    Flux<String> dfsWithPlans(Instant from, Instant to);

    /** The stretches a fiduciary was on a plan within the period, with the plan's price now. */
    Flux<InvoiceCalc.Segment> segments(String dfId, Instant from, Instant to);

    /** Ends the fiduciary's open plan stretch at {@code at}: it is billed no further. */
    Mono<Void> endBilling(String dfId, Instant at);

    /**
     * Opens a plan stretch again from {@code at}, on the plan the fiduciary's subscription has now, unless one is already open (ADR-109):
     * a departure that was called off. Running it again, or for a fiduciary that is still billed, changes nothing. The time the
     * fiduciary was not billed stays unbilled.
     */
    Mono<Void> resumeBilling(String dfId, Instant at);

    /**
     * Issues the invoice with the next number of its kind and year, unless the fiduciary already has one for that period
     * (then empty). The number is given here, so none is skipped by an invoice that fails to save.
     */
    Mono<Invoice> issue(Invoice draft);

    Mono<Invoice> find(String id);

    /** Newest first; either filter may be null. */
    Flux<Invoice> list(String dfId, Instant periodFrom);

    /** ISSUED to PAID; empty if it is not ISSUED. */
    Mono<Invoice> markPaid(String id, Instant at, String note);

    /** ISSUED or PAID to CREDITED; empty if it is neither. */
    Mono<Invoice> markCredited(String id);
}
