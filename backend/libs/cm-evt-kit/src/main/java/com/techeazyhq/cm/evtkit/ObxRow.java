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
