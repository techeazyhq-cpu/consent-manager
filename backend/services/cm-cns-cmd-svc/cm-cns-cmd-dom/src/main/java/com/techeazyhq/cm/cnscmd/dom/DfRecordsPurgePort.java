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

import java.time.Instant;

import reactor.core.publisher.Mono;

/**
 * Deletes what this service holds about a closed fiduciary once its retention has passed (ADR-064): consent requests with their consents, records, events and receipts, then, when none is left, its purposes, webhook endpoints and batches.
 * Only whole units older than {@code before} go, at most {@code limit} a call.
 */
public interface DfRecordsPurgePort {

    /** {@code deleted} in this call; {@code remaining} of the fiduciary held afterwards. */
    record Purged(long deleted, long remaining) {
    }

    Mono<Purged> purge(String dfId, Instant before, int limit);
}
