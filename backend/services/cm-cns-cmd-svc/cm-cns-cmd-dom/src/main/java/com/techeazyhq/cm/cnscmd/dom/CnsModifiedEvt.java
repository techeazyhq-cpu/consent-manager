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
 * The principal narrowed a consent (ADR-026 §4; ISO/IEC TS 27560 "modified"): ended it earlier, or withdrew one data
 * category. Carries the consent as it now stands, so every reader, the fiduciary included, sees the new scope and end.
 *
 * @param change     which narrowing it was
 * @param dCat       the category withdrawn, for {@link Change#CATEGORY_WITHDRAWN}; null otherwise
 * @param scp        the categories the consent now covers
 * @param valPrd     its validity now
 */
public record CnsModifiedEvt(UUID evtId, Instant occurredAt, String cnsArtId, String cnsReqId, String dfId,
                             DpRef dpRef, Prps prps, Change change, String dCat, Scp scp, ValPrd valPrd)
        implements DomainEvt {

    public enum Change {
        SHORTENED,
        CATEGORY_WITHDRAWN
    }

    static CnsModifiedEvt of(CnsArt art, Change change, String dCat, Instant at) {
        return new CnsModifiedEvt(UUID.randomUUID(), at, art.id(), art.cnsReqId(), art.dfId(), art.dpRef(), art.prps(),
                change, dCat, art.scp(), art.valPrd());
    }
}
