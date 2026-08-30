package com.techeazyhq.cm.cnscmd.dom;

/**
 * Write-side status of a {@link CnsArt}, aligned to the whitepaper's HL7-FHIR-inspired
 * lifecycle (plan §1) minus {@code AMENDED}, deferred to Phase 2.
 */
public enum CnsArtSts {
    ISSUED,
    REVOKED,
    EXPIRED
}
