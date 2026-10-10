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

/** Factory Method (plan §12): a {@link CnsReq} can never be constructed in an invalid state. */
public final class CnsReqFactory {

    private CnsReqFactory() {
    }

    public static CnsReq create(String dfId, DpRef dpRef, Prps prps, Scp scp, String ntcTitle, String ntcText) {
        if (dfId == null || dfId.isBlank()) {
            throw new IllegalArgumentException("dfId must not be blank");
        }
        if (ntcTitle == null || ntcTitle.isBlank()) {
            throw new IllegalArgumentException("notice title must not be blank");
        }
        String id = UUID.randomUUID().toString();
        CnsReq cnsReq = new CnsReq(id, dfId, dpRef, prps, scp, ntcTitle, ntcText, CnsReqSts.PENDING, Instant.now());
        cnsReq.markCreated();
        return cnsReq;
    }

    /** Rehydration from persistence — no invariant re-checking, no event raised. */
    public static CnsReq rehydrate(String id, String dfId, DpRef dpRef, Prps prps, Scp scp, String ntcTitle,
                                    String ntcText, CnsReqSts sts, Instant createdAt) {
        return new CnsReq(id, dfId, dpRef, prps, scp, ntcTitle, ntcText, sts, createdAt);
    }
}
