package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/** Raised by the scheduled {@code CnsExpirySweepJob} (plan §8) when a ValPrd-lapsed CnsArt is swept. */
public record CnsExpiredEvt(UUID evtId, Instant occurredAt, String cnsArtId) implements DomainEvt {

    public static CnsExpiredEvt of(String cnsArtId) {
        return new CnsExpiredEvt(UUID.randomUUID(), Instant.now(), cnsArtId);
    }
}
