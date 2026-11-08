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

package com.techeazyhq.cm.ntf.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.ntf.dom.NtfChnl;
import com.techeazyhq.cm.ntf.dom.NtfDeliveryRejectedException;
import com.techeazyhq.cm.ntf.dom.NtfMsg;
import com.techeazyhq.cm.ntf.dom.OtpSendLedgerPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** This service's own limit on the sign-in codes it will send, whoever asks (ADR-080). */
class OtpSendLimitTest {

    private final List<NtfMsg> sent = new ArrayList<>();
    private boolean smsFails;

    /** Counts the same way the database does: per destination and per channel, in one window (the hour and day collapse for the test). */
    private static final class Ledger implements OtpSendLedgerPort {
        final Map<Long, String[]> rows = new HashMap<>();
        final AtomicLong ids = new AtomicLong();

        @Override
        public Mono<Long> claim(String hash, NtfChnl channel, Limits limits) {
            long perDest = rows.values().stream().filter(r -> r[0].equals(hash)).count();
            long perChannel = rows.values().stream().filter(r -> r[1].equals(channel.name())).count();
            boolean over = (limits.perDestinationPerHour() != 0 && perDest >= limits.perDestinationPerHour())
                    || (limits.perChannelPerHour() != 0 && perChannel >= limits.perChannelPerHour());
            if (over) {
                return Mono.empty();
            }
            long id = ids.incrementAndGet();
            rows.put(id, new String[] {hash, channel.name()});
            return Mono.just(id);
        }

        @Override
        public Mono<Void> release(long place) {
            rows.remove(place);
            return Mono.empty();
        }
    }

    private final Ledger ledger = new Ledger();
    private final NtfDispatchService dispatch = new NtfDispatchService(
            msg -> Mono.fromRunnable(() -> sent.add(msg)),
            msg -> smsFails ? Mono.error(new NtfDeliveryRejectedException("INVALID_RECIPIENT", "not a number")) : Mono.fromRunnable(() -> sent.add(msg)),
            msg -> Mono.error(new AssertionError("no WhatsApp")),
            msg -> Mono.error(new AssertionError("no push")));

    private OtpSendCmdHandler handler(int perDestination, int perChannel) {
        return new OtpSendCmdHandler(dispatch, ledger, new EmailFingerprint("a-test-key"),
                new OtpSendLedgerPort.Limits(perDestination, 1000, perChannel));
    }

    private static OtpSendCmd email(String to) {
        return new OtpSendCmd(NtfChnl.EMAIL, to, "123456");
    }

    @Test
    void codesAreSentUpToTheLimitForOneDestinationAndNoFurther() {
        OtpSendCmdHandler h = handler(3, 0);
        for (int i = 0; i < 3; i++) {
            h.handle(email("a@example.com")).block();
        }

        DomRuleViolation refused = assertThrows(DomRuleViolation.class, () -> h.handle(email("a@example.com")).block());
        assertEquals(429, refused.httpStatus());
        assertEquals("otp-send-limited", refused.problemType());
        assertEquals(3, sent.size(), "the fourth was not sent");
        h.handle(email("someone-else@example.com")).block();
        assertEquals(4, sent.size(), "another destination is not affected");
    }

    @Test
    void aTagOnAnEmailAddressDoesNotMakeItAnotherMailbox() {
        OtpSendCmdHandler h = handler(2, 0);
        h.handle(email("a+1@example.com")).block();
        h.handle(email("A+2@Example.com")).block();

        assertThrows(DomRuleViolation.class, () -> h.handle(email("a+3@example.com")).block());
        assertThrows(DomRuleViolation.class, () -> h.handle(email("a@example.com")).block());
    }

    @Test
    void aPhoneNumberIsItsDigitsWhateverTheSpacing() {
        OtpSendCmdHandler h = handler(2, 0);
        h.handle(new OtpSendCmd(NtfChnl.SMS, "+91 98765 43210", "1")).block();
        h.handle(new OtpSendCmd(NtfChnl.SMS, "+919876543210", "2")).block();

        assertThrows(DomRuleViolation.class, () -> h.handle(new OtpSendCmd(NtfChnl.SMS, "+91-98765-43210", "3")).block());
    }

    @Test
    void theSameAddressByEmailAndBySmsAreCountedApartBecauseTheyAreNotTheSameDestination() {
        OtpSendCmdHandler h = handler(1, 0);
        h.handle(email("a@example.com")).block();
        h.handle(new OtpSendCmd(NtfChnl.SMS, "123456789", "1")).block();

        assertEquals(2, sent.size());
    }

    @Test
    void aChannelHasABudgetOfItsOwnWhoeverAsks() {
        OtpSendCmdHandler h = handler(0, 2);
        h.handle(email("a@example.com")).block();
        h.handle(email("b@example.com")).block();

        assertThrows(DomRuleViolation.class, () -> h.handle(email("c@example.com")).block());
        h.handle(new OtpSendCmd(NtfChnl.SMS, "123456789", "1")).block();
        assertEquals(3, sent.size(), "SMS has its own budget");
    }

    @Test
    void aCodeThatCouldNotBeSentGivesItsPlaceBack() {
        OtpSendCmdHandler h = handler(1, 0);
        smsFails = true;
        assertThrows(NtfDeliveryRejectedException.class, () -> h.handle(new OtpSendCmd(NtfChnl.SMS, "123456789", "1")).block());
        assertTrue(ledger.rows.isEmpty(), "the failed attempt did not use the allowance");

        smsFails = false;
        h.handle(new OtpSendCmd(NtfChnl.SMS, "123456789", "2")).block();
        assertEquals(1, sent.size());
    }

    @Test
    void aLimitOfZeroIsOff() {
        OtpSendCmdHandler h = handler(0, 0);
        for (int i = 0; i < 50; i++) {
            h.handle(email("a@example.com")).block();
        }
        assertEquals(50, sent.size());
    }

    @Test
    void theNormalisationItselfIsWhatTheDocumentationSays() {
        assertEquals("a@example.com", OtpSendCmdHandler.normalise(NtfChnl.EMAIL, " A+tag@Example.com "));
        assertEquals("a@example.com", OtpSendCmdHandler.normalise(NtfChnl.EMAIL, "a@example.com"));
        assertEquals("+tag@example.com", OtpSendCmdHandler.normalise(NtfChnl.EMAIL, "+tag@example.com"), "a leading plus is part of the name");
        assertEquals("919876543210", OtpSendCmdHandler.normalise(NtfChnl.SMS, "+91 (98765) 43210"));
    }
}
