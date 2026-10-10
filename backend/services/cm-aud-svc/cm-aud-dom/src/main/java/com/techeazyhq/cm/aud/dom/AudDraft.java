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

package com.techeazyhq.cm.aud.dom;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * A platform event about to be recorded in the audit ledger (ADR-014). The fields that go into the hash chain are
 * deliberately free of personal data: the event's id, type and time, and the ids of the consent request, artefact and
 * fiduciary it concerns. Anything personal (the principal's id, email or phone, the notice text) stays in
 * {@code payloadJson} and {@code dpId}, which are kept outside the chain so they can be erased without breaking it.
 *
 * @param payloadJson the event as received, with any consent token replaced by its SHA-256
 * @param dpId        the linked principal the event concerns, for their own view of the ledger; null if none yet
 */
public record AudDraft(UUID evtId, String evtType, Instant occurredAt, String cnsReqId, String cnsArtId, String dfId,
                       String dpId, String payloadJson) {

    public AudDraft {
        if (evtId == null || evtType == null || evtType.isBlank() || occurredAt == null || payloadJson == null) {
            throw new IllegalArgumentException("An audit draft needs an event id, type, time and payload");
        }
        occurredAt = occurredAt.truncatedTo(ChronoUnit.MICROS);
    }
}
