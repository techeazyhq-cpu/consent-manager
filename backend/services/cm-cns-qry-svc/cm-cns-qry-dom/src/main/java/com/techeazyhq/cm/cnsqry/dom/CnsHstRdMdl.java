package com.techeazyhq.cm.cnsqry.dom;

import java.time.Instant;

/** Append-only audit trail row (plan §8 — Stage 5 "Audit & Lineage"). */
public record CnsHstRdMdl(String cnsReqId, CnsLcySts fromSts, CnsLcySts toSts, Instant occurredAt, String evtId) {
}
