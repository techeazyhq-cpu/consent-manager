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
        Instant updatedAt,
        String ntcTitle,
        String ntcText,
        Set<String> grantedDCatCodes) {

    /** The same record in a new state; everything the fiduciary asked, the notice included, stays as it was. */
    public CnsArtRdMdl with(String newCnsArtId, String newDpId, CnsLcySts newSts, Instant newValidFrom,
                            Instant newValidUntil, Instant newUpdatedAt) {
        return new CnsArtRdMdl(cnsReqId, newCnsArtId, dfId, newDpId, prpsCode, dCatCodes, newSts, newValidFrom,
                newValidUntil, createdAt, newUpdatedAt, ntcTitle, ntcText, grantedDCatCodes);
    }

    /** What the principal granted (ADR-026): some or all of {@code dCatCodes}. */
    public CnsArtRdMdl granted(Set<String> codes) {
        return new CnsArtRdMdl(cnsReqId, cnsArtId, dfId, dpId, prpsCode, dCatCodes, lcySts, validFrom, validUntil,
                createdAt, updatedAt, ntcTitle, ntcText, codes);
    }
}
