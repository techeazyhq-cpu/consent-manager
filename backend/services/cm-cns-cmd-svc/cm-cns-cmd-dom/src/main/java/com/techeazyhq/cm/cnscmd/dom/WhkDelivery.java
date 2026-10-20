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

package com.techeazyhq.cm.cnscmd.dom;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * One message on its way to one endpoint (ADR-047), written in the same transaction as the change it reports, so it
 * is delivered at least once. A failure is retried on {@link #RETRY_DELAYS}; after the last, it is dead-lettered
 * ({@link Sts#FAILED}) until the fiduciary replays it.
 *
 * @param msgId the message's id ({@code webhook-id}), the same on every attempt and every replay
 */
public record WhkDelivery(String id, String endpointId, String dfId, String msgId, String type, String payload, Sts sts,
                          int attempts, Instant nextAttemptAt, Integer lastStatus, String lastError, Instant deliveredAt,
                          Instant createdAt) {

    public enum Sts {
        PENDING,
        DELIVERED,
        /** Dead-lettered: every retry failed. A replay sends it again. */
        FAILED
    }

    /** After each failed attempt, the wait before the next: 8 attempts over about 45 hours. */
    public static final List<Duration> RETRY_DELAYS = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6), Duration.ofHours(12), Duration.ofHours(24));

    /** One attempt's outcome: an HTTP status, or no response and why. */
    public record Attempt(int n, Instant at, Integer status, String error, long millis) {

        public boolean succeeded() {
            return status != null && status >= 200 && status < 300;
        }
    }

    /** The delivery after {@code attempt}: delivered, retried later, or dead-lettered after the last retry. */
    public WhkDelivery after(Attempt attempt, List<Duration> delays) {
        if (attempt.succeeded()) {
            return new WhkDelivery(id, endpointId, dfId, msgId, type, payload, Sts.DELIVERED, attempt.n(), null,
                    attempt.status(), null, attempt.at(), createdAt);
        }
        Optional<Duration> wait = attempt.n() <= delays.size() ? Optional.of(delays.get(attempt.n() - 1)) : Optional.empty();
        return new WhkDelivery(id, endpointId, dfId, msgId, type, payload, wait.isPresent() ? Sts.PENDING : Sts.FAILED,
                attempt.n(), wait.map(attempt.at()::plus).orElse(null), attempt.status(), attempt.error(), null,
                createdAt);
    }

    /** Sent again from now (a replay): the same message, the same id, its attempts counted on. */
    public WhkDelivery replayed(Instant now) {
        return new WhkDelivery(id, endpointId, dfId, msgId, type, payload, Sts.PENDING, attempts, now, lastStatus,
                lastError, deliveredAt, createdAt);
    }
}
