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
 * Stage 4: Asynchronous Revocation (plan §7c step 5). Data-blind constraint: this event
 * intentionally carries only artefact/purpose identifiers — no DF-internal purge targets
 * (unlike the whitepaper's illustrative {@code cascade_instructions} payload) since CM has
 * no visibility into a DF's downstream stores; each subscriber owns that mapping itself
 * (docs/adr/adr-006-consent-token-and-pep-pdp.md). The producer serializes this to the
 * external wire shape ({@code sub_did}/{@code fiduciary_id}/{@code purpose_code}) — see
 * {@code PulsarCnsEvtProducer} in cm-cns-cmd-infra.
 */
public record CnsRevokedEvt(UUID evtId, Instant occurredAt, String cnsArtId, DpRef dpRef, String dfId, Prps prps,
                            String cnsReqId)
        implements DomainEvt {

    public static CnsRevokedEvt of(String cnsArtId, DpRef dpRef, String dfId, Prps prps, String cnsReqId) {
        return new CnsRevokedEvt(UUID.randomUUID(), Instant.now(), cnsArtId, dpRef, dfId, prps, cnsReqId);
    }
}
