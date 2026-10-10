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

package com.techeazyhq.cm.evtkit;

import java.time.Instant;
import java.util.UUID;

/**
 * Shape of the {@code outbox_evt} table every write-side service owns (plan §7c/§12,
 * Outbox pattern): the aggregate's own R2DBC transaction inserts one row per raised
 * {@code DomainEvt}; a relay polls rows where {@code publishedAt IS NULL} and publishes
 * them to Pulsar, marking {@code publishedAt} on success. Atomic DB-write + event-publish
 * without a distributed transaction.
 */
public record ObxRow(
        UUID id,
        UUID evtId,
        String aggType,
        String aggId,
        String evtType,
        String payloadJson,
        Instant createdAt,
        Instant publishedAt) {

    public static ObxRow pending(UUID evtId, String aggType, String aggId, String evtType, String payloadJson) {
        return new ObxRow(UUID.randomUUID(), evtId, aggType, aggId, evtType, payloadJson, Instant.now(), null);
    }

    public boolean isPublished() {
        return publishedAt != null;
    }
}
