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
 * The principal renewed a consent before it lapsed (ADR-026 §4; ISO/IEC TS 27560 "renewed consent given"): the only
 * way to extend one. The new validity runs from the renewal for the days they chose, never past the purpose's maximum.
 */
public record CnsRenewedEvt(UUID evtId, Instant occurredAt, String cnsArtId, String cnsReqId, String dfId, DpRef dpRef,
                            Prps prps, Scp scp, ValPrd valPrd, int validityDays)
        implements DomainEvt {

    static CnsRenewedEvt of(CnsArt art, int validityDays, Instant at) {
        return new CnsRenewedEvt(UUID.randomUUID(), at, art.id(), art.cnsReqId(), art.dfId(), art.dpRef(), art.prps(),
                art.scp(), art.valPrd(), validityDays);
    }
}
