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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.idn.otp.OtpAbuseGuard.Admission;
import com.techeazyhq.cm.idn.otp.OtpAbuseGuard.Denial;
import com.techeazyhq.cm.idn.otp.OtpAbuseGuard.Limit;
import com.techeazyhq.cm.idn.otp.OtpAbuseGuard.Limits;

class OtpAbuseGuardTest {

    /** A shared store whose entries expire as the test moves its clock, as Keycloak's cache expires them. */
    private static final class FakeSlots implements OtpAbuseGuard.Slots {
        Instant now = Instant.parse("2026-11-07T10:00:00Z");
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

        void advance(Duration by) {
            now = now.plus(by);
        }
    }

    private final FakeSlots slots = new FakeSlots();
    private final OtpAbuseGuard guard = new OtpAbuseGuard(slots, Limits.defaults());

    private static OtpIdentifier email(String address) {
        return OtpIdentifier.parse(address).orElseThrow();
    }

    private static Denial denial(Admission admission) {
        return admission.denial();
    }

    @Test
    void anAddressGetsFiveCodesAnHourAndThenWaits() {
        OtpIdentifier victim = email("victim@example.com");
        for (int i = 0; i < 5; i++) {
            assertTrue(guard.admitSend(victim, "10.0.0." + i).admitted(), "code " + (i + 1));
        }

        assertEquals(Denial.TOO_MANY_CODES, denial(guard.admitSend(victim, "10.0.0.9")));

        // the window slides: an hour after the first, one more is allowed
        slots.advance(Duration.ofMinutes(61));
        assertTrue(guard.admitSend(victim, "10.0.0.9").admitted());
    }

    @Test
    void theDayCapHoldsEvenWhenEveryHourIsWithinItsLimit() {
        OtpIdentifier victim = email("victim@example.com");
        int sent = 0;
        // five an hour for three hours is fifteen: the day allows ten
        for (int hour = 0; hour < 3; hour++) {
            for (int i = 0; i < 5; i++) {
                if (guard.admitSend(victim, null).admitted()) {
                    sent++;
                }
            }
            slots.advance(Duration.ofMinutes(61));
        }

        assertEquals(10, sent);
        assertEquals(Denial.TOO_MANY_CODES, denial(guard.admitSend(victim, null)));
        slots.advance(Duration.ofHours(24));
        assertTrue(guard.admitSend(victim, null).admitted());
    }

    @Test
    void plusAddressingDoesntMakeANewMailbox() {
        for (int i = 0; i < 5; i++) {
            assertTrue(guard.admitSend(email("victim+" + i + "@example.com"), null).admitted());
        }

        assertEquals(Denial.TOO_MANY_CODES, denial(guard.admitSend(email("victim+another@example.com"), null)));
        assertEquals(Denial.TOO_MANY_CODES, denial(guard.admitSend(email("VICTIM@example.com"), null)));
        // another mailbox, a lookalike domain, and a phone number are separate
        assertTrue(guard.admitSend(email("someone@example.com"), null).admitted());
        assertTrue(guard.admitSend(email("victim@example.org"), null).admitted());
        assertTrue(guard.admitSend(email("+919876543210"), null).admitted());
    }

    @Test
    void oneNetworkAddressCantSpreadTheSameAttackOverManyAddresses() {
        OtpAbuseGuard guard = new OtpAbuseGuard(slots, new Limits(Limits.defaults().sendsPerAddress(),
                List.of(new Limit(30, Duration.ofHours(1))), Limits.defaults().wrongGuessesPerAddress()));
        for (int i = 0; i < 30; i++) {
            assertTrue(guard.admitSend(email("person" + i + "@example.com"), "203.0.113.7").admitted(), "request " + i);
        }

        assertEquals(Denial.NETWORK_BUSY, denial(guard.admitSend(email("person30@example.com"), "203.0.113.7")));
        // another network is unaffected, and so is a request whose network isn't known
        assertTrue(guard.admitSend(email("person30@example.com"), "203.0.113.8").admitted());
        assertTrue(guard.admitSend(email("person31@example.com"), null).admitted());
    }

    @Test
    void theDefaultNetworkLimitLeavesRoomForEveryoneBehindOneAddress() {
        // a carrier's shared address or an office: a few hundred people signing in within an hour is normal
        for (int i = 0; i < 300; i++) {
            assertTrue(guard.admitSend(email("person" + i + "@example.com"), "100.64.0.1").admitted(), "person " + i);
        }
        assertEquals(Denial.NETWORK_BUSY, denial(guard.admitSend(email("person300@example.com"), "100.64.0.1")));
    }

    @Test
    void aRefusalGivesBackWhatItClaimedSoTheAddressIsntChargedForIt() {
        OtpAbuseGuard guard = new OtpAbuseGuard(slots, new Limits(Limits.defaults().sendsPerAddress(),
                List.of(new Limit(30, Duration.ofHours(1))), Limits.defaults().wrongGuessesPerAddress()));
        for (int i = 0; i < 30; i++) {
            guard.admitSend(email("filler" + i + "@example.com"), "198.51.100.1");
        }
        OtpIdentifier person = email("person@example.com");

        // refused for the network: the address's own allowance must be untouched
        for (int i = 0; i < 10; i++) {
            assertEquals(Denial.NETWORK_BUSY, denial(guard.admitSend(person, "198.51.100.1")));
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(guard.admitSend(person, "192.0.2." + i).admitted(), "code " + (i + 1));
        }
    }

    @Test
    void aCodeThatWasntSentIsGivenBack() {
        OtpIdentifier person = email("person@example.com");
        for (int i = 0; i < 4; i++) {
            assertTrue(guard.admitSend(person, null).admitted());
        }
        Admission fifth = guard.admitSend(person, "192.0.2.1");

        guard.release(fifth.permit());

        // the failed one didn't count: the fifth can be taken again, and only then is it full
        assertTrue(guard.admitSend(person, "192.0.2.1").admitted());
        assertEquals(Denial.TOO_MANY_CODES, denial(guard.admitSend(person, "192.0.2.1")));
        guard.release(null);
    }

    @Test
    void tenWrongCodesForAnAddressStopBothCodesAndGuessesForAnHour() {
        OtpIdentifier victim = email("victim@example.com");
        for (int i = 0; i < 9; i++) {
            guard.recordWrongGuess(victim);
        }
        assertFalse(guard.wrongGuessesExhausted(victim));
        assertTrue(guard.admitSend(victim, null).admitted());

        guard.recordWrongGuess(victim);

        assertTrue(guard.wrongGuessesExhausted(victim));
        assertEquals(Denial.TOO_MANY_WRONG_GUESSES, denial(guard.admitSend(victim, null)));
        // it applies to this address only
        assertFalse(guard.wrongGuessesExhausted(email("other@example.com")));

        slots.advance(Duration.ofMinutes(61));
        assertFalse(guard.wrongGuessesExhausted(victim));
        assertTrue(guard.admitSend(victim, null).admitted());
    }

    @Test
    void wrongGuessesAreCountedForAnAddressWithNoAccountToo() {
        // the guard never asks whether an account exists: a new principal's address is limited the same way
        OtpIdentifier stranger = email("nobody-has-this-address@example.com");
        for (int i = 0; i < 10; i++) {
            guard.recordWrongGuess(stranger);
        }

        assertTrue(guard.wrongGuessesExhausted(stranger));
    }

    @Test
    void theDayBudgetOutlastsTheHourOne() {
        OtpIdentifier victim = email("victim@example.com");
        // ten an hour, twenty a day: two bad hours exhaust the day
        for (int hour = 0; hour < 2; hour++) {
            for (int i = 0; i < 10; i++) {
                guard.recordWrongGuess(victim);
            }
            slots.advance(Duration.ofMinutes(61));
        }

        assertTrue(guard.wrongGuessesExhausted(victim), "an hour on, but the day's twenty are spent");
        slots.advance(Duration.ofHours(24));
        assertFalse(guard.wrongGuessesExhausted(victim));
    }

    @Test
    void aLimitOfZeroSwitchesThatLimitOff() {
        OtpAbuseGuard open = new OtpAbuseGuard(slots, new Limits(
                List.of(new Limit(0, Duration.ofHours(1))), List.of(new Limit(0, Duration.ofHours(1))),
                List.of(new Limit(0, Duration.ofHours(1)))));
        OtpIdentifier person = email("person@example.com");

        for (int i = 0; i < 100; i++) {
            assertTrue(open.admitSend(person, "192.0.2.1").admitted());
            open.recordWrongGuess(person);
        }
        assertFalse(open.wrongGuessesExhausted(person));
    }

    @Test
    void nothingIsKeptInTheStoreThatNamesTheAddressOrTheNetwork() {
        guard.admitSend(email("private.person@example.com"), "203.0.113.77");
        guard.recordWrongGuess(email("private.person@example.com"));

        assertTrue(slots.expiry.size() >= 5);
        for (String key : slots.expiry.keySet()) {
            assertTrue(key.startsWith("cm-otp:"), key);
            assertFalse(key.contains("private") || key.contains("example.com") || key.contains("203.0.113"), key);
        }
    }

    @Test
    void theAddressKeyIgnoresCaseAndPlusTagsButNotTheKind() {
        assertEquals(OtpAbuseGuard.addressKey(email("a@x.com")), OtpAbuseGuard.addressKey(email("A+tag@X.com")));
        assertNotEquals(OtpAbuseGuard.addressKey(email("a@x.com")), OtpAbuseGuard.addressKey(email("b@x.com")));
        assertNotEquals(OtpAbuseGuard.addressKey(email("+919876543210")), OtpAbuseGuard.addressKey(email("+919876543211")));
        assertNull(guard.admitSend(email("a@x.com"), null).denial());
    }
}
