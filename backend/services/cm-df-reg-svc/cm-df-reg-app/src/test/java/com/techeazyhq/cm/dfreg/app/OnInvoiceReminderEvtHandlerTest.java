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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.dfreg.app.OnInvoiceReminderEvtHandler.Reminder;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** An overdue invoice is told to the fiduciary by email, once per step, without the address ever travelling in an event (ADR-105). */
class OnInvoiceReminderEvtHandlerTest {

    private record Sent(String to, String subject, String body, String ref) {
    }

    private final List<Sent> sent = new ArrayList<>();
    private Df df;

    private OnInvoiceReminderEvtHandler handler() {
        DfRepoPort repo = new DfRepoPort() {
            @Override
            public Mono<Df> save(Df d) {
                return Mono.just(d);
            }

            @Override
            public Mono<Df> findById(String id) {
                return Mono.justOrEmpty(df != null && df.id().equals(id) ? df : null);
            }

            @Override
            public Flux<Df> findAll() {
                return df == null ? Flux.empty() : Flux.just(df);
            }
        };
        return new OnInvoiceReminderEvtHandler(repo, (to, subject, body, ref) -> Mono.fromRunnable(() -> sent.add(new Sent(to, subject, body, ref))));
    }

    private Df fiduciary(String contact) {
        return com.techeazyhq.cm.dfreg.dom.DfFactory.register("Acme Retail", contact,
                new com.techeazyhq.cm.dfreg.dom.DfPrfl("Acme Retail Pvt Ltd", null, null, null, null, null, null,
                        "Asha", "dpo@acme.example"), "FREE");
    }

    private Reminder reminder(String dfId, int step) {
        return new Reminder(dfId, "inv-1", "INV-2026-000007", 118_000, "INR", Instant.parse("2026-11-15T00:00:00Z"), step);
    }

    @Test
    void theFiduciaryIsToldWhichInvoiceHowMuchAndSinceWhen() {
        df = fiduciary("owner@acme.example");

        handler().handle(reminder(df.id(), 10)).block();

        assertEquals(1, sent.size());
        Sent s = sent.get(0);
        assertEquals("owner@acme.example", s.to());
        assertEquals("Invoice INV-2026-000007 is 10 days past its due date", s.subject());
        assertTrue(s.body().contains("INV-2026-000007"));
        assertTrue(s.body().contains("1180.00 INR"), s.body());
        assertTrue(s.body().contains("15 November 2026"), s.body());
        assertTrue(s.body().contains("Acme Retail"));
    }

    @Test
    void eachStepHasItsOwnReferenceSoARedeliveredEventIsOneEmail() {
        df = fiduciary("owner@acme.example");

        handler().handle(reminder(df.id(), 3)).block();
        handler().handle(reminder(df.id(), 10)).block();

        assertEquals(List.of("cm-invoice-inv-1-3", "cm-invoice-inv-1-10"), sent.stream().map(Sent::ref).toList());
    }

    @Test
    void aFiduciaryThatIsUnknownGetsNothing() {
        df = fiduciary("owner@acme.example");
        handler().handle(reminder("someone-else", 3)).block();

        assertTrue(sent.isEmpty());
    }

    @Test
    void amountsAreMinorUnitsWithTwoPlaces() {
        assertEquals("1180.00 INR", OnInvoiceReminderEvtHandler.amount(118_000, "INR"));
        assertEquals("0.05 USD", OnInvoiceReminderEvtHandler.amount(5, "USD"));
        assertFalse(OnInvoiceReminderEvtHandler.amount(118_000, "INR").contains(","));
    }
}
