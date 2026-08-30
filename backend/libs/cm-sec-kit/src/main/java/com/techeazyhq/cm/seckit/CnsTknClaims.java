package com.techeazyhq.cm.seckit;

import java.time.Instant;
import java.util.List;

/**
 * The {@code CnsTkn} payload — external wire contract, field names taken verbatim from the
 * user's whitepaper's HL7-FHIR-inspired schema (plan §13), NOT the internal abbreviation
 * glossary, since this crosses organizational boundaries (a Data Fiduciary validates it).
 */
public record CnsTknClaims(
        String subDid,
        String fiduciaryId,
        String purposeCode,
        AccessType accessType,
        List<String> scope,
        Instant exp) {

    public enum AccessType {
        ALLOW,
        DENY
    }
}
