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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.bil.dom.InvoiceReminderEvt;
import com.techeazyhq.cm.bil.dom.InvoiceReminderPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** The reminder sweep raises each step of each overdue unpaid invoice once, however often it runs (ADR-105). */
class InvoiceReminderSweepTest {

    private static final InvoiceReminderPolicy POLICY = new InvoiceReminderPolicy(14, List.of(3, 10, 20));

    private final List<InvoiceReminderPort.Unpaid> unpaid = new ArrayList<>();
    private final Set<String> stored = new HashSet<>();
    private final List<InvoiceReminderEvt> events = new ArrayList<>();
    private final List<String> audited = new ArrayList<>();
    private final List<Instant> askedBefore = new ArrayList<>();
    private boolean failOnInv2;

    private final InvoiceReminderPort port = new InvoiceReminderPort() {
        @Override
        public Flux<Unpaid> unpaidIssuedBefore(Instant issuedBefore) {
            askedBefore.add(issuedBefore);
            return Flux.fromIterable(unpaid).filter(u -> !u.issuedAt().isAfter(issuedBefore));
        }

        @Override
        public Mono<Boolean> addIfNew(String invoiceId, int stepDays, Instant raisedAt) {
            return Mono.just(stored.add(invoiceId + "/" + stepDays));
        }
    };

    private final InvoiceReminderHandler handler = new InvoiceReminderHandler(port,
            evt -> failOnInv2 && evt.number().equals("INV-2026-000002")
                    ? Mono.error(new IllegalStateException("outbox down")) : Mono.fromRunnable(() -> events.add(evt)),
            (action, dfId, target, details) -> Mono.fromRunnable(() -> audited.add(action + " " + dfId + " " + target)));

    private InvoiceReminderSweep sweepAt(String now) {
        return new InvoiceReminderSweep(port, handler, POLICY, Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private void invoice(int n, String issuedAt, long total) {
        unpaid.add(new InvoiceReminderPort.Unpaid("id-" + n, String.format("INV-2026-%06d", n), "df-" + n, total, "INR", Instant.parse(issuedAt)));
    }

    @Test
    void anInvoiceThatIsNotYetOverdueIsLeftAlone() {
        invoice(1, "2026-11-01T00:00:00Z", 118_000);
        // due 15 Nov; the first step is 18 Nov
        assertEquals(0L, sweepAt("2026-11-17T23:00:00Z").run().block());
        assertEquals(List.of(), events);
    }

    @Test
    void anInvoiceAtItsFirstStepIsRemindedAboutOnceHoweverOftenItIsSwept() {
        invoice(1, "2026-11-01T00:00:00Z", 118_000);

        assertEquals(1L, sweepAt("2026-11-18T01:00:00Z").run().block());
        assertEquals(0L, sweepAt("2026-11-18T02:00:00Z").run().block());
        assertEquals(0L, sweepAt("2026-11-19T02:00:00Z").run().block());

        assertEquals(1, events.size());
        InvoiceReminderEvt e = events.get(0);
        assertEquals("df-1", e.dfId());
        assertEquals("INV-2026-000001", e.number());
        assertEquals(118_000, e.totalMinor());
        assertEquals("INR", e.currency());
        assertEquals(Instant.parse("2026-11-15T00:00:00Z"), e.dueAt());
        assertEquals(3, e.stepDays());
        assertEquals(List.of("invoice.reminder df-1 INV-2026-000001/3"), audited);
    }

    @Test
    void eachLaterStepIsRaisedOnceWhenItsDaysHavePassed() {
        invoice(1, "2026-11-01T00:00:00Z", 118_000);

        sweepAt("2026-11-18T01:00:00Z").run().block();
        assertEquals(1L, sweepAt("2026-11-25T01:00:00Z").run().block());
        assertEquals(1L, sweepAt("2026-12-05T01:00:00Z").run().block());
        assertEquals(0L, sweepAt("2027-03-01T01:00:00Z").run().block(), "after the last step nothing more is sent");

        assertEquals(List.of(3, 10, 20), events.stream().map(InvoiceReminderEvt::stepDays).toList());
    }

    @Test
    void aSweepThatWasDownSendsOnlyTheLatestStep() {
        invoice(1, "2026-11-01T00:00:00Z", 118_000);

        assertEquals(1L, sweepAt("2026-11-27T00:00:00Z").run().block());
        assertEquals(List.of(10), events.stream().map(InvoiceReminderEvt::stepDays).toList());
    }

    @Test
    void eachInvoiceIsItsOwnReminder() {
        invoice(1, "2026-11-01T00:00:00Z", 118_000);
        invoice(2, "2026-10-01T00:00:00Z", 59_000);
        invoice(3, "2026-11-20T00:00:00Z", 10_000);

        assertEquals(2L, sweepAt("2026-12-01T00:00:00Z").run().block());
        assertEquals(Set.of("df-1", "df-2"), Set.copyOf(events.stream().map(InvoiceReminderEvt::dfId).toList()));
    }

    @Test
    void oneInvoiceThatFailsDoesNotStopTheOthers() {
        invoice(1, "2026-11-01T00:00:00Z", 118_000);
        invoice(2, "2026-10-01T00:00:00Z", 59_000);
        failOnInv2 = true;

        assertEquals(1L, sweepAt("2026-12-01T00:00:00Z").run().block());
        assertEquals(List.of("INV-2026-000001"), events.stream().map(InvoiceReminderEvt::number).toList());
    }

    @Test
    void theSweepLooksBackOnlyAsFarAsTheFirstStep() {
        sweepAt("2026-12-01T00:00:00Z").run().block();
        assertEquals(List.of(Instant.parse("2026-11-14T00:00:00Z")), askedBefore);
    }

    @Test
    void noStepsMeansNoReminders() {
        invoice(1, "2026-01-01T00:00:00Z", 118_000);
        InvoiceReminderSweep none = new InvoiceReminderSweep(port, handler, new InvoiceReminderPolicy(14, List.of()),
                Clock.fixed(Instant.parse("2026-12-01T00:00:00Z"), ZoneOffset.UTC));

        assertEquals(0L, none.run().block());
        assertEquals(List.of(), askedBefore, "it does not even read the invoices");
    }
}
