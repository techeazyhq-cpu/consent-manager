package com.techeazyhq.cm.cnsqry.dom;

import java.time.Instant;
import java.util.Set;

/**
 * The denormalized read-model row (plan §8) — one per request/artefact, {@code dpId} nullable
 * until linked (plan §7a), {@code cnsArtId}/{@code validUntil} nullable until granted.
 */
public record CnsArtRdMdl(
        String cnsReqId,
        String cnsArtId,
        String dfId,
        String dpId,
        String prpsCode,
        Set<String> dCatCodes,
        CnsLcySts lcySts,
        Instant validFrom,
        Instant validUntil,
        Instant createdAt,
        Instant updatedAt) {
}
