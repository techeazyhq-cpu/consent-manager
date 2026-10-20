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
import java.util.List;

/**
 * A consent's ISO/IEC TS 27560 record (ADR-025), from the request to its end: the header that never changes, and its
 * events, appended in the same transaction as each change of the consent. Its Notice and Processing sections come
 * from the purpose version the request names, which is immutable (ADR-043), so the record never needs a copy of them
 * to stay true.
 *
 * @param dpId the principal, pseudonymous; null until the request is linked to an account (no contact data, ever)
 */
public record CnsRec(String id, String cnsReqId, String dfId, String dpId, String prpsCode, PrpsVerRef prpsVer,
                     Instant createdAt, List<CnsRecEvt> events) {

    public CnsRec {
        events = events == null ? List.of() : List.copyOf(events);
    }

    /** The record as it stood at event {@code seq}: what a receipt for that event shows. */
    public CnsRec upTo(int seq) {
        return new CnsRec(id, cnsReqId, dfId, dpId, prpsCode, prpsVer, createdAt,
                events.stream().filter(e -> e.seq() <= seq).toList());
    }
}
