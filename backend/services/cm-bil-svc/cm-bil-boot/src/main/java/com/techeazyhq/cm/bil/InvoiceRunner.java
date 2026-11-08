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

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.techeazyhq.cm.bil.app.InvoiceCmds;

/**
 * Issues last month's invoices (ADR-079). It looks every hour and does the work once a month has ended: a fiduciary already
 * invoiced for it is left alone, so running again, or two replicas running together, issues nothing twice (the invoice's
 * unique key per fiduciary and period decides).
 */
@Component
@ConditionalOnProperty(name = "cm.bil.invoicing.enabled", havingValue = "true", matchIfMissing = true)
public class InvoiceRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InvoiceRunner.class);

    private final InvoiceCmds cmds;
    private final Clock clock;
    private final AtomicBoolean passRunning = new AtomicBoolean();

    public InvoiceRunner(InvoiceCmds cmds, Clock clock) {
        this.cmds = cmds;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${cm.bil.invoicing.initial-delay-ms:120000}",
            fixedDelayString = "${cm.bil.invoicing.fixed-delay-ms:3600000}")
    public void run() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        YearMonth previous = YearMonth.now(clock.withZone(ZoneOffset.UTC)).minusMonths(1);
        cmds.runFor(previous).doFinally(signal -> passRunning.set(false))
                .count()
                .subscribe(n -> {
                    if (n > 0) {
                        LOG.info("Issued {} invoice(s) for {}", n, previous);
                    }
                }, e -> LOG.warn("[CM-INVOICE-RUN-FAILED] The invoice run for {} failed", previous, e));
    }
}
