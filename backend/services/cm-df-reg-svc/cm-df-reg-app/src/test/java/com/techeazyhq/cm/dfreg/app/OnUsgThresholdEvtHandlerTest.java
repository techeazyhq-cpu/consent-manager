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

import com.techeazyhq.cm.dfreg.app.OnUsgThresholdEvtHandler.Threshold;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class OnUsgThresholdEvtHandlerTest {

    private record Sent(String to, String subject, String body, String ref) {
    }

    private static final Instant RESETS = Instant.parse("2026-12-01T00:00:00Z");
    private final List<Sent> sent = new ArrayList<>();
    private Df df;

    private DfRepoPort repo() {
        return new DfRepoPort() {
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
    }

    private OnUsgThresholdEvtHandler handler(List<String> platform) {
        return new OnUsgThresholdEvtHandler(repo(),
                (to, subject, body, ref) -> Mono.fromRunnable(() -> sent.add(new Sent(to, subject, body, ref))),
                platform);
    }

    private static Threshold threshold(int percent, long used) {
        return new Threshold("df-1", "2026-11", percent, used, 1_000, "FREE", RESETS);
    }

    private Df fiduciary(String contact) {
        return com.techeazyhq.cm.dfreg.dom.DfFactory.register("Acme Retail", contact,
                new com.techeazyhq.cm.dfreg.dom.DfPrfl("Acme Retail Pvt Ltd", null, null, null, null, null, null,
                        "Asha", "dpo@acme.example"), "FREE");
    }

    @Test
    void theFiduciaryAndThePlatformAreEmailedWithAStableReference() {
        df = fiduciary("owner@acme.example");
        String id = df.id();
        OnUsgThresholdEvtHandler handler = handler(List.of("ops@cm.example", "admin@cm.example"));

        handler.handle(new Threshold(id, "2026-11", 80, 800, 1_000, "FREE", RESETS)).block();

        assertEquals(List.of("owner@acme.example", "ops@cm.example", "admin@cm.example"),
                sent.stream().map(Sent::to).toList());
        assertEquals("You've used 80% of this month's consent request allowance", sent.get(0).subject());
        assertTrue(sent.get(0).body().contains("Acme Retail has used 800 of its 1000 consent requests for 2026-11"));
        assertTrue(sent.get(0).body().contains("starts again on 1 December 2026"));
        assertTrue(sent.get(0).body().contains("ask for a larger plan in the DF Console"));
        assertEquals("Usage alert: Acme Retail at 80% of its 2026-11 allowance", sent.get(1).subject());
        // delivered twice, the same references: one email each to the mail service
        assertEquals("cm-usage-" + id + "-2026-11-80-df", sent.get(0).ref());
        assertEquals("cm-usage-" + id + "-2026-11-80-platform-0", sent.get(1).ref());
        assertEquals("cm-usage-" + id + "-2026-11-80-platform-1", sent.get(2).ref());
    }

    @Test
    void usedUpSaysNewRequestsAreRefused() {
        df = fiduciary("owner@acme.example");

        handler(List.of()).handle(new Threshold(df.id(), "2026-11", 100, 1_000, 1_000, "FREE", RESETS)).block();

        assertEquals(1, sent.size());
        assertEquals("Your consent request allowance for 2026-11 is used up", sent.get(0).subject());
        assertTrue(sent.get(0).body().contains("New consent requests are refused until then."));
        assertFalse(sent.get(0).body().contains("Once it is used up"));
    }

    @Test
    void anUnknownFiduciaryIsIgnoredNotRetriedForever() {
        df = null;

        handler(List.of("ops@cm.example")).handle(threshold(80, 800)).block();

        assertTrue(sent.isEmpty());
    }

    @Test
    void theBodyNamesNothingPersonal() {
        assertFalse(OnUsgThresholdEvtHandler.platformBody("Acme", threshold(80, 800)).contains("@"));
    }
}
