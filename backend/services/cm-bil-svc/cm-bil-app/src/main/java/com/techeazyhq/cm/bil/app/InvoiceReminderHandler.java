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

import java.time.Instant;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.bil.dom.InvoiceReminderEvt;
import com.techeazyhq.cm.bil.dom.InvoiceReminderEvtPort;
import com.techeazyhq.cm.bil.dom.InvoiceReminderPort;

import reactor.core.publisher.Mono;

/**
 * Raises one reminder (ADR-105): records the step, and in the same transaction writes the event that tells the fiduciary and the audit
 * entry. If the step was already raised nothing else is written, so a sweep repeated every hour, or run by two replicas, raises each
 * step of each invoice once.
 */
public class InvoiceReminderHandler {

    private final InvoiceReminderPort reminders;
    private final InvoiceReminderEvtPort events;
    private final AuditTrail audit;

    public InvoiceReminderHandler(InvoiceReminderPort reminders, InvoiceReminderEvtPort events, AuditTrail audit) {
        this.reminders = reminders;
        this.events = events;
        this.audit = audit;
    }

    /** True when the step was new. */
    @Transactional
    public Mono<Boolean> raise(InvoiceReminderPort.Unpaid invoice, Instant dueAt, int stepDays, Instant now) {
        return reminders.addIfNew(invoice.invoiceId(), stepDays, now).flatMap(isNew -> !isNew ? Mono.just(false)
                : events.reminderDue(new InvoiceReminderEvt(UUID.randomUUID(), now, invoice.dfId(), invoice.invoiceId(),
                                invoice.number(), invoice.totalMinor(), invoice.currency(), dueAt, stepDays))
                        .then(audit.record("invoice.reminder", invoice.dfId(), invoice.number() + "/" + stepDays,
                                AuditTrail.details("invoice", invoice.number(), "daysOverdue", String.valueOf(stepDays))))
                        .thenReturn(true));
    }
}
