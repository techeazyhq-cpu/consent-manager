package com.techeazyhq.cm.cnsqry.dom;

/**
 * Unified read-side lifecycle status (plan §8) — a read-side simplification distinct from
 * the write side's two aggregate-specific enums ({@code CnsReqSts}/{@code CnsArtSts} in
 * cm-cns-cmd-svc), since the query side presents one lifecycle view regardless of which
 * write aggregate currently owns the record.
 */
public enum CnsLcySts {
    PENDING,
    DENIED,
    ISSUED,
    REVOKED,
    NOTIFICATION_FAILED,
    EXPIRED
}
