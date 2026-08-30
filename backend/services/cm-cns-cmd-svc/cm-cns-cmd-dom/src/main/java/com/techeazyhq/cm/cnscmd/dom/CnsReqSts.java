package com.techeazyhq.cm.cnscmd.dom;

/** Write-side status of a {@link CnsReq} (plan §1: kept separate from {@link CnsArtSts}). */
public enum CnsReqSts {
    PENDING,
    GRANTED,
    DENIED,
    EXPIRED,
    NOTIFICATION_FAILED
}
