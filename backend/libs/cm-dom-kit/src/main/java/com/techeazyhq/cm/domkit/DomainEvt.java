package com.techeazyhq.cm.domkit;

import java.time.Instant;
import java.util.UUID;

/**
 * Marker for a domain event raised by an {@link Agg}. {@code evtId} is the idempotency key
 * every Pulsar consumer in this platform dedupes on (at-least-once delivery, §7c of the plan).
 */
public interface DomainEvt {
    UUID evtId();

    Instant occurredAt();
}
