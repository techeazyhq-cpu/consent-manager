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
 * A receipt was issued (ADR-025). Carries its hash, not its content, so the audit ledger chains it and the next
 * RFC 3161-anchored checkpoint gives it trusted time (ADR-015) without the ledger holding the record.
 */
public record CnsRcptIssuedEvt(UUID evtId, Instant occurredAt, String rcptId, String recId, String cnsReqId, String dfId,
                               int seq, String kid, String sha256) implements DomainEvt {

    public static CnsRcptIssuedEvt of(CnsRcpt rcpt, String dfId) {
        return new CnsRcptIssuedEvt(UUID.randomUUID(), rcpt.issuedAt(), rcpt.id(), rcpt.recId(), rcpt.cnsReqId(), dfId,
                rcpt.seq(), rcpt.kid(), rcpt.sha256());
    }
}
