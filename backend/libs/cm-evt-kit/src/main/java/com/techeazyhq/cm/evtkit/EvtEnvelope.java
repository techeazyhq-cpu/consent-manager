package com.techeazyhq.cm.evtkit;

import java.time.Instant;
import java.util.UUID;

/**
 * Wire envelope for every event published to Pulsar. {@code payloadJson} is the
 * event-specific body serialized by the producer; consumers dedupe on {@code evtId}
 * since Pulsar delivery is at-least-once (plan §7c).
 */
public record EvtEnvelope(
        UUID evtId,
        String evtType,
        Instant occurredAt,
        String producedBy,
        String payloadJson) {
}
