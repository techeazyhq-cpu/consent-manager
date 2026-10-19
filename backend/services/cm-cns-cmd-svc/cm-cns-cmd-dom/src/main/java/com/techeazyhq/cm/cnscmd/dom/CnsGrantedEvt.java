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

package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * Stage 2 decided: approve (plan §7c step 3). Carries the signed {@code CnsTkn}, the artefact's ValPrd, and what was
 * granted (ADR-026): {@code scp} may be fewer categories than the request asked for, and {@code validityDays} is the
 * validity the principal chose.
 */
public record CnsGrantedEvt(
        UUID evtId, Instant occurredAt, String cnsReqId, String cnsArtId, String dfId, DpRef dpRef, String cnsTkn,
        ValPrd valPrd, Scp scp, int validityDays)
        implements DomainEvt {

    public static CnsGrantedEvt of(String cnsReqId, String cnsArtId, String dfId, DpRef dpRef, String cnsTkn,
                                   CnsGrant grant) {
        return new CnsGrantedEvt(UUID.randomUUID(), Instant.now(), cnsReqId, cnsArtId, dfId, dpRef, cnsTkn,
                grant.valPrd(), grant.scp(), grant.validityDays());
    }
}
