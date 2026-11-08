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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the status page says, worked out from probe results (ADR-067): kept apart from the probing and the storage so it can be
 * tested without either.
 */
public final class StatusMath {

    /** A component's state as people read it. */
    public enum State { OPERATIONAL, DEGRADED, OUTAGE }

    /** The platform as a whole. */
    public enum Overall { OPERATIONAL, DEGRADED, MAJOR_OUTAGE }

    /** One kind of notice staff post. */
    public enum Severity { INFO, MAINTENANCE, INCIDENT }

    private StatusMath() {
    }

    /**
     * A single failed probe is a blip, not an outage, so it shows as degraded; two in a row (a minute at the usual 30 second
     * interval) is an outage; a success clears it at once.
     */
    public static State stateOf(int consecutiveFailures) {
        return consecutiveFailures <= 0 ? State.OPERATIONAL : consecutiveFailures == 1 ? State.DEGRADED : State.OUTAGE;
    }

    /** Whether the platform is working as a whole: a critical component in outage is a major outage; anything else off is degraded. */
    public static Overall overall(Map<String, State> states, List<String> critical) {
        boolean anyOff = false;
        for (Map.Entry<String, State> e : states.entrySet()) {
            if (e.getValue() == State.OUTAGE && critical.contains(e.getKey())) {
                return Overall.MAJOR_OUTAGE;
            }
            anyOff |= e.getValue() != State.OPERATIONAL;
        }
        return anyOff ? Overall.DEGRADED : Overall.OPERATIONAL;
    }

    /**
     * The share of probes that succeeded, in percent with two decimals, or null when there were none (a component that has not been
     * watched yet has no uptime to claim, rather than a perfect one).
     */
    public static Double uptimePercent(long ok, long total) {
        if (total <= 0) {
            return null;
        }
        return Math.round(10_000.0 * ok / total) / 100.0;
    }

    /** The probes' hour buckets a window covers, newest first: the hour of {@code now} and the ones before it. */
    public static List<Long> hoursIn(Instant now, Duration window) {
        long current = now.getEpochSecond() / 3600;
        long count = Math.max(1, window.toHours());
        return java.util.stream.LongStream.range(0, count).mapToObj(i -> current - i).toList();
    }

    /** A notice is shown while it is open, and for a week after it was resolved so people can see what happened. */
    public static boolean visible(Instant resolvedAt, Instant now) {
        return resolvedAt == null || resolvedAt.isAfter(now.minus(Duration.ofDays(7)));
    }
}
