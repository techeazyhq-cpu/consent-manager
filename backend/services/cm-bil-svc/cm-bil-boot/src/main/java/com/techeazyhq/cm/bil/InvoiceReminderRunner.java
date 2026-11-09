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

package com.techeazyhq.cm.bil;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.techeazyhq.cm.bil.app.InvoiceReminderSweep;

/**
 * Runs the invoice reminder sweep (ADR-105) every hour. One pass at a time per replica; two replicas sweeping together are safe, since
 * the step's primary key lets only one raise it.
 */
@Component
public class InvoiceReminderRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceReminderRunner.class);

    private final InvoiceReminderSweep sweep;
    private final AtomicBoolean passRunning = new AtomicBoolean();

    public InvoiceReminderRunner(InvoiceReminderSweep sweep) {
        this.sweep = sweep;
    }

    @Scheduled(initialDelayString = "${cm.bil.invoice-reminders.initial-delay-ms:120000}",
            fixedDelayString = "${cm.bil.invoice-reminders.fixed-delay-ms:3600000}")
    public void run() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        sweep.run().doFinally(signal -> passRunning.set(false))
                .subscribe(raised -> {
                    if (raised > 0) {
                        LOG.info("Raised {} invoice reminder(s)", raised);
                    }
                }, e -> LOG.warn("[CM-INVOICE-REMINDER-FAILED] The invoice reminder sweep failed", e));
    }
}
