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

/**
 * Factory Method (plan §12). {@code issue} deliberately does not raise {@code CnsGrantedEvt}
 * itself — that event must carry the signed {@code CnsTkn}, which only exists once
 * {@code cm-sec-kit} (outside the domain layer) signs it against this aggregate's id; the
 * command handler composes and publishes that event once signing completes (plan §7c step 3).
 */
public final class CnsArtFactory {

    private CnsArtFactory() {
    }

    public static CnsArt issue(String cnsReqId, String dfId, DpRef dpRef, Prps prps, Scp scp, ValPrd valPrd) {
        if (valPrd.isExpiredAt(Instant.now())) {
            throw new IllegalArgumentException("ValPrd must not already be expired at issuance");
        }
        String id = UUID.randomUUID().toString();
        return new CnsArt(id, cnsReqId, dfId, dpRef, prps, scp, valPrd, CnsArtSts.ISSUED, Instant.now(), null);
    }

    /** Rehydration from persistence — no invariant re-checking, no event raised. */
    public static CnsArt rehydrate(String id, String cnsReqId, String dfId, DpRef dpRef, Prps prps, Scp scp,
                                    ValPrd valPrd, CnsArtSts sts, Instant issuedAt, String cnsTkn) {
        return new CnsArt(id, cnsReqId, dfId, dpRef, prps, scp, valPrd, sts, issuedAt, cnsTkn);
    }
}
