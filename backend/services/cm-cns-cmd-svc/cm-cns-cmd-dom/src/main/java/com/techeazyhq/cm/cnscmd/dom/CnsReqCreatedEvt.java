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

/** Stage 1: Capture (plan §7c step 1). Published to {@code cm.cnscmd.evt.cns-req-created.v1}. */
/**
 * A fiduciary asked a person for consent. It carries the notice the fiduciary wrote, so the person sees what they're
 * asked to agree to wherever their consents are shown (DPDPA s.5, GDPR art. 7): a decision is only informed if the
 * notice is in front of them. The notice is the fiduciary's text, not personal data; {@code prpsVer} says which
 * purpose version and notice it came from (ADR-031).
 */
public record CnsReqCreatedEvt(UUID evtId, Instant occurredAt, String cnsReqId, String dfId, DpRef dpRef, Prps prps,
                                Scp scp, String ntcTitle, String ntcText, PrpsVerRef prpsVer)
        implements DomainEvt {

    public static CnsReqCreatedEvt of(String cnsReqId, String dfId, DpRef dpRef, CnsReqTemplate t) {
        return new CnsReqCreatedEvt(UUID.randomUUID(), Instant.now(), cnsReqId, dfId, dpRef, t.prps(), t.scp(),
                t.ntcTitle(), t.ntcText(), t.prpsVer());
    }
}
