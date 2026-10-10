/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

package com.techeazyhq.cm.seckit;

import java.time.Instant;
import java.util.List;

/**
 * The {@code CnsTkn} payload — external wire contract, field names taken verbatim from the
 * user's whitepaper's HL7-FHIR-inspired schema (plan §13), NOT the internal abbreviation
 * glossary, since this crosses organizational boundaries (a Data Fiduciary validates it).
 * {@code consentArtefactId} ({@code cns_art_id}) binds the token to one artefact, so a token cannot be replayed
 * against another artefact's live status (ADR-006).
 */
public record CnsTknClaims(
        String consentArtefactId,
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
