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

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.techeazyhq.cm.bil.dom.InvoiceReminderPort;

import reactor.core.publisher.Mono;

/**
 * Looks, every hour, for invoices that are past their due date and unpaid, and raises the reminder for the latest step each has reached
 * (ADR-105). Paying or crediting an invoice stops its reminders, because it is no longer issued and unpaid. A failure on one invoice
 * is logged and does not stop the others.
 */
public class InvoiceReminderSweep {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceReminderSweep.class);
    private static final int CONCURRENCY = 2;

    private final InvoiceReminderPort invoices;
    private final InvoiceReminderHandler handler;
    private final InvoiceReminderPolicy policy;
    private final Clock clock;

    public InvoiceReminderSweep(InvoiceReminderPort invoices, InvoiceReminderHandler handler, InvoiceReminderPolicy policy, Clock clock) {
        this.invoices = invoices;
        this.handler = handler;
        this.policy = policy;
        this.clock = clock;
    }

    /** How many reminders this pass raised. */
    public Mono<Long> run() {
        Instant now = clock.instant();
        Instant cutoff = policy.firstStepIssuedBefore(now);
        if (cutoff == null) {
            return Mono.just(0L);
        }
        return invoices.unpaidIssuedBefore(cutoff)
                .flatMap(invoice -> {
                    Integer step = policy.stepReached(invoice.issuedAt(), now);
                    if (step == null) {
                        return Mono.just(false);
                    }
                    return handler.raise(invoice, policy.dueAt(invoice.issuedAt()), step, now)
                            .onErrorResume(e -> {
                                LOG.warn("[CM-INVOICE-REMINDER-FAILED] An invoice reminder could not be raised for {}", invoice.number(), e);
                                return Mono.just(false);
                            });
                }, CONCURRENCY)
                .filter(Boolean::booleanValue)
                .count();
    }
}
