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

package com.techeazyhq.cm.dfreg.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.EmailPort;

import reactor.core.publisher.Mono;

/**
 * Tells a fiduciary that an invoice is past its due date and unpaid (ADR-105). cm-bil-svc finds the overdue invoice and says so in an
 * event; cm-df-reg-svc holds the fiduciary's contact address, so it sends the email, through cm-ntf-svc, and the address never travels in an
 * event or over HTTP. Idempotent: each message has a reference built from the invoice and the step, so an event delivered twice is one
 * email to the mail service.
 */
public class OnInvoiceReminderEvtHandler {

    private static final Logger LOG = LoggerFactory.getLogger(OnInvoiceReminderEvtHandler.class);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    /** What cm-bil-svc's {@code InvoiceReminderEvt} says. */
    public record Reminder(String dfId, String invoiceId, String number, long totalMinor, String currency, Instant dueAt, int stepDays) {

        String ref() {
            return "cm-invoice-" + invoiceId + "-" + stepDays;
        }
    }

    private final DfRepoPort dfs;
    private final EmailPort email;

    public OnInvoiceReminderEvtHandler(DfRepoPort dfs, EmailPort email) {
        this.dfs = dfs;
        this.email = email;
    }

    public Mono<Void> handle(Reminder r) {
        return dfs.findById(r.dfId())
                .switchIfEmpty(Mono.fromRunnable(() -> LOG.warn("Invoice reminder for unknown fiduciary {}", r.dfId())))
                .flatMap(df -> df.contactEmail() == null || df.contactEmail().isBlank() ? Mono.<Void>empty()
                        : email.send(df.contactEmail(), subject(r), body(df.name(), r), r.ref()));
    }

    static String subject(Reminder r) {
        return "Invoice " + r.number() + " is " + r.stepDays() + " days past its due date";
    }

    /** An amount in minor units as a decimal with two places, then its currency code: 118000 INR is "1180.00 INR". */
    static String amount(long minor, String currency) {
        return BigDecimal.valueOf(minor, 2).toPlainString() + " " + currency;
    }

    static String body(String name, Reminder r) {
        return "Hello,\n\nInvoice " + r.number() + " for " + name + ", " + amount(r.totalMinor(), r.currency()) + ", was due on "
                + DAY.format(r.dueAt()) + " and has not been marked as paid.\n\nIf you have already paid it, please send the payment "
                + "reference to the Consent Manager team so it can be recorded, and you will get no more reminders about it. If not, "
                + "an Owner or an Administrator can see the invoice in the DF Console, under Billing.\n\n"
                + "This is an automated message from Consent Manager.";
    }
}
