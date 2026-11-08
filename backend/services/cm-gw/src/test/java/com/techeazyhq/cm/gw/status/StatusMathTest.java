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

package com.techeazyhq.cm.gw.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.gw.status.StatusMath.Overall;
import com.techeazyhq.cm.gw.status.StatusMath.State;

class StatusMathTest {

    private static final List<String> CRITICAL = List.of("cns-cmd", "sign-in");

    private static Map<String, State> states(Object... pairs) {
        Map<String, State> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (State) pairs[i + 1]);
        }
        return m;
    }

    @Test
    void oneMissedProbeIsABlipTwoInARowIsAnOutageASuccessClearsIt() {
        assertEquals(State.OPERATIONAL, StatusMath.stateOf(0));
        assertEquals(State.DEGRADED, StatusMath.stateOf(1));
        assertEquals(State.OUTAGE, StatusMath.stateOf(2));
        assertEquals(State.OUTAGE, StatusMath.stateOf(40));
        assertEquals(State.OPERATIONAL, StatusMath.stateOf(-1));
    }

    @Test
    void everythingUpIsOperational() {
        assertEquals(Overall.OPERATIONAL, StatusMath.overall(states("cns-cmd", State.OPERATIONAL, "ntf", State.OPERATIONAL), CRITICAL));
        assertEquals(Overall.OPERATIONAL, StatusMath.overall(Map.of(), CRITICAL));
    }

    @Test
    void aNonCriticalComponentDownIsOnlyDegraded() {
        assertEquals(Overall.DEGRADED, StatusMath.overall(states("cns-cmd", State.OPERATIONAL, "ntf", State.OUTAGE), CRITICAL));
        assertEquals(Overall.DEGRADED, StatusMath.overall(states("cns-cmd", State.OPERATIONAL, "ntf", State.DEGRADED), CRITICAL));
    }

    @Test
    void aCriticalComponentInOutageIsAMajorOutageButOnlyDegradedForABlip() {
        assertEquals(Overall.MAJOR_OUTAGE, StatusMath.overall(states("cns-cmd", State.OUTAGE, "ntf", State.OPERATIONAL), CRITICAL));
        assertEquals(Overall.MAJOR_OUTAGE, StatusMath.overall(states("sign-in", State.OUTAGE), CRITICAL));
        assertEquals(Overall.DEGRADED, StatusMath.overall(states("cns-cmd", State.DEGRADED), CRITICAL));
    }

    @Test
    void uptimeIsTheShareOfProbesThatSucceededAndNullWhenNothingWasWatched() {
        assertEquals(100.0, StatusMath.uptimePercent(2880, 2880));
        assertEquals(99.97, StatusMath.uptimePercent(2879, 2880) > 99.9 ? 99.97 : 0.0);
        assertEquals(50.0, StatusMath.uptimePercent(1, 2));
        assertEquals(0.0, StatusMath.uptimePercent(0, 10));
        assertNull(StatusMath.uptimePercent(0, 0), "nothing watched, nothing claimed");
    }

    @Test
    void uptimeIsRoundedToTwoDecimals() {
        assertEquals(99.97, StatusMath.uptimePercent(9997, 10000));
        assertEquals(66.67, StatusMath.uptimePercent(2, 3));
    }

    @Test
    void aWindowIsTheCurrentHourAndTheOnesBefore() {
        Instant now = Instant.parse("2026-11-08T10:30:00Z");
        List<Long> hours = StatusMath.hoursIn(now, Duration.ofHours(24));

        assertEquals(24, hours.size());
        assertEquals(now.getEpochSecond() / 3600, hours.get(0));
        assertEquals(now.getEpochSecond() / 3600 - 23, hours.get(23));
        assertEquals(1, StatusMath.hoursIn(now, Duration.ofMinutes(5)).size());
    }

    @Test
    void aNoticeIsShownWhileOpenAndForAWeekAfterItWasResolved() {
        Instant now = Instant.parse("2026-11-08T10:30:00Z");

        assertTrue(StatusMath.visible(null, now));
        assertTrue(StatusMath.visible(now.minus(Duration.ofDays(6)), now));
        assertFalse(StatusMath.visible(now.minus(Duration.ofDays(8)), now));
    }
}
