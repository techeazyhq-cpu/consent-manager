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
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.bil.dom.Invoice;
import com.techeazyhq.cm.bil.dom.InvoiceCalc;
import com.techeazyhq.cm.bil.dom.InvoiceRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Invoicing (ADR-079): the monthly run, marking an invoice paid, crediting one, and plan prices. Months are calendar months in
 * UTC, invoiced once they have ended; running a month again issues nothing new for a fiduciary already invoiced for it.
 */
public class InvoiceCmds {

    private final InvoiceRepoPort repo;
    private final AuditTrail audit;
    private final Clock clock;
    private final String currency;
    private final String taxLabel;
    private final int taxBps;

    public InvoiceCmds(InvoiceRepoPort repo, AuditTrail audit, Clock clock, String currency, String taxLabel, int taxBps) {
        this.repo = repo;
        this.audit = audit;
        this.clock = clock;
        this.currency = currency;
        this.taxLabel = taxLabel;
        this.taxBps = taxBps;
    }

    /** The invoices newly issued for the month; fiduciaries already invoiced for it, or with nothing to pay, add none. */
    public Flux<Invoice> runFor(YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        if (to.isAfter(clock.instant())) {
            return Flux.error(new DomRuleViolation(409, "billing-period-open",
                    month + " has not ended: invoices are issued for months that have"));
        }
        return repo.dfsWithPlans(from, to).concatMap(dfId -> invoiceOne(dfId, from, to));
    }

    private Mono<Invoice> invoiceOne(String dfId, Instant from, Instant to) {
        return repo.segments(dfId, from, to).collectList()
                .map(segments -> InvoiceCalc.calculate(from, to, segments, taxBps))
                .filter(draft -> draft.totalMinor() > 0)
                .flatMap(draft -> repo.issue(new Invoice(UUID.randomUUID().toString(), null, Invoice.Kind.INVOICE, dfId, from, to,
                        currency, draft.subtotalMinor(), taxLabel, taxBps, draft.taxMinor(), draft.totalMinor(),
                        Invoice.Status.ISSUED, clock.instant(), null, null, null, null, draft.lines())))
                .flatMap(inv -> audit.record("invoice.issued", dfId, inv.id(),
                        AuditTrail.details("number", inv.number(), "total", String.valueOf(inv.totalMinor()), "currency", inv.currency()))
                        .thenReturn(inv));
    }

    @Transactional
    public Mono<Invoice> markPaid(String id, String note) {
        return found(id).flatMap(inv -> repo.markPaid(id, clock.instant(), note == null ? "" : note.strip())
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(409, "invoice-not-payable",
                        inv.number() + " is " + inv.sts() + ": only an issued invoice is marked paid")))
                .flatMap(paid -> audit.record("invoice.paid", paid.dfId(), paid.id(), AuditTrail.details("number", paid.number()))
                        .thenReturn(paid)));
    }

    /** Reverses an invoice in full with a credit note of its own number; an invoice is credited once. */
    @Transactional
    public Mono<Invoice> creditNote(String id, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            return Mono.error(new DomRuleViolation(400, "invalid-credit-reason", "Give a reason of up to 500 characters"));
        }
        return found(id).flatMap(inv -> {
            if (inv.kind() != Invoice.Kind.INVOICE || inv.sts() == Invoice.Status.CREDITED) {
                return Mono.<Invoice>error(new DomRuleViolation(409, "invoice-already-credited",
                        inv.number() + " is not an invoice that can be credited"));
            }
            return repo.issue(inv.creditNote(UUID.randomUUID().toString(), null, reason.strip(), clock.instant()))
                    // a credit note already exists (an earlier attempt stopped half way): finish marking the invoice, and say so
                    .switchIfEmpty(Mono.defer(() -> repo.markCredited(id).then(Mono.<Invoice>error(new DomRuleViolation(409,
                            "invoice-already-credited", inv.number() + " has been credited already")))))
                    .flatMap(note -> repo.markCredited(id).thenReturn(note))
                    .flatMap(note -> audit.record("invoice.credited", note.dfId(), note.id(),
                            AuditTrail.details("invoice", inv.number(), "creditNote", note.number())).thenReturn(note));
        });
    }

    @Transactional
    public Mono<InvoiceRepoPort.PlanPrice> setPrice(String planName, long monthlyMinor) {
        if (monthlyMinor < 0 || monthlyMinor > 1_000_000_000_00L) {
            return Mono.error(new DomRuleViolation(400, "invalid-price", "The monthly price is from 0 up to a thousand million"));
        }
        return repo.setPrice(planName, monthlyMinor, clock.instant())
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "sub-pln-not-found", "No SubPln " + planName)))
                .flatMap(p -> audit.record("plan.price-set", null, p.planName(),
                        AuditTrail.details("plan", p.planName(), "monthlyMinor", String.valueOf(p.monthlyMinor()))).thenReturn(p));
    }

    private Mono<Invoice> found(String id) {
        return repo.find(id).switchIfEmpty(Mono.error(() -> new DomRuleViolation(404, "invoice-not-found", "There is no such invoice")));
    }
}
