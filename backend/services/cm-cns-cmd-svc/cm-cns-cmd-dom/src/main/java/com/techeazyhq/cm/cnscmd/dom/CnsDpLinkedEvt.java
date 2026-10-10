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
 * Raised by {@code LinkDpCmdHandler} once per {@link CnsReq}/{@link CnsArt} record it
 * retroactively links (plan §7a). Consumed by cm-cns-qry-svc's Projector to set {@code dp_id}
 * on the matching read-model rows.
 */
public record CnsDpLinkedEvt(UUID evtId, Instant occurredAt, String recordId, String recordType, String newDpId)
        implements DomainEvt {

    public static CnsDpLinkedEvt of(String recordId, String recordType, String newDpId) {
        return new CnsDpLinkedEvt(UUID.randomUUID(), Instant.now(), recordId, recordType, newDpId);
    }
}
