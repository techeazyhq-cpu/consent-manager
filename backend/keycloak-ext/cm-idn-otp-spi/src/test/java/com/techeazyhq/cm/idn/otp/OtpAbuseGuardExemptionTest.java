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

package com.techeazyhq.cm.idn.otp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.idn.otp.OtpAbuseGuard.Denial;
import com.techeazyhq.cm.idn.otp.OtpAbuseGuard.Limit;
import com.techeazyhq.cm.idn.otp.OtpAbuseGuard.Limits;

/**
 * The local stack's exemption (ADR-061): named addresses are not held to the limit on codes sent to one address, so a
 * test kit can sign in as the same few accounts all day. It lifts that one limit for exactly those addresses and nothing else.
 */
class OtpAbuseGuardExemptionTest {

    private static final class FakeSlots implements OtpAbuseGuard.Slots {
        final Instant now = Instant.parse("2026-11-07T10:00:00Z");
        final Map<String, Instant> expiry = new HashMap<>();

        @Override
        public boolean claim(String key, Duration ttl) {
            if (taken(key)) {
                return false;
            }
            expiry.put(key, now.plus(ttl));
            return true;
        }

        @Override
        public boolean taken(String key) {
            Instant until = expiry.get(key);
            return until != null && now.isBefore(until);
        }

        @Override
        public void release(String key) {
            expiry.remove(key);
        }
    }

    private static OtpIdentifier email(String address) {
        return OtpIdentifier.parse(address).orElseThrow();
    }

    private final FakeSlots slots = new FakeSlots();
    private final OtpAbuseGuard guard = new OtpAbuseGuard(slots, Limits.defaults(), OtpAbuseGuard.parseExempt("owner@example.test"));

    @Test
    void anExemptAddressCanSignInAsOftenAsItLikes() {
        for (int i = 0; i < 50; i++) {
            assertTrue(guard.admitSend(email("owner@example.test"), "10.0.0." + (i % 5)).admitted(), "sign-in " + i);
        }
    }

    @Test
    void onlyTheExactAddressIsExemptNotItsAliasesOrOthers() {
        for (int i = 0; i < 5; i++) {
            assertTrue(guard.admitSend(email("owner+" + i + "@example.test"), null).admitted());
        }
        assertEquals(Denial.TOO_MANY_CODES, guard.admitSend(email("owner+6@example.test"), null).denial(), "a +tag is another address");
        for (int i = 0; i < 5; i++) {
            assertTrue(guard.admitSend(email("someone-else@example.test"), null).admitted());
        }
        assertEquals(Denial.TOO_MANY_CODES, guard.admitSend(email("someone-else@example.test"), null).denial());
    }

    @Test
    void anExemptAddressCanStillNotBeGuessed() {
        OtpIdentifier owner = email("owner@example.test");
        for (int i = 0; i < 10; i++) {
            guard.recordWrongGuess(owner);
        }

        assertTrue(guard.wrongGuessesExhausted(owner));
        assertEquals(Denial.TOO_MANY_WRONG_GUESSES, guard.admitSend(owner, null).denial(), "no code, and none accepted");
    }

    @Test
    void theNetworkLimitStillAppliesToAnExemptAddress() {
        OtpAbuseGuard strict = new OtpAbuseGuard(slots, new Limits(Limits.defaults().sendsPerAddress(),
                List.of(new Limit(3, Duration.ofHours(1))), Limits.defaults().wrongGuessesPerAddress()),
                OtpAbuseGuard.parseExempt("owner@example.test"));
        for (int i = 0; i < 3; i++) {
            assertTrue(strict.admitSend(email("owner@example.test"), "203.0.113.9").admitted());
        }

        assertEquals(Denial.NETWORK_BUSY, strict.admitSend(email("owner@example.test"), "203.0.113.9").denial());
    }

    @Test
    void theListIsReadAsSignInReadsAddressesAndATypoExemptsNothing() {
        assertEquals(Set.of("a@example.test", "b@example.test", "+919876543210"),
                OtpAbuseGuard.parseExempt(" A@Example.test, b@example.test\n+91 98765 43210 ,, "));
        assertEquals(Set.of("a@example.test"), OtpAbuseGuard.parseExempt("a@example.test, not-an-address, @nowhere, 12345"));
        assertEquals(Set.of(), OtpAbuseGuard.parseExempt(null));
        assertEquals(Set.of(), OtpAbuseGuard.parseExempt("   "));
        assertEquals(Set.of(), OtpAbuseGuard.parseExempt("everyone, *, .*"));
    }

    @Test
    void withNothingSetNobodyIsExempt() {
        OtpAbuseGuard plain = new OtpAbuseGuard(slots, Limits.defaults());
        for (int i = 0; i < 5; i++) {
            assertTrue(plain.admitSend(email("owner@example.test"), null).admitted());
        }

        assertEquals(Denial.TOO_MANY_CODES, plain.admitSend(email("owner@example.test"), null).denial());
    }
}
