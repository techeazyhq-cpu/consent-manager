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

package com.techeazyhq.cm.cnsqry.dom;

import java.time.Instant;

/** Append-only audit trail row (plan §8 — Stage 5 "Audit & Lineage"). */
/**
 * One entry in a consent's history. {@code chg} names a change that kept it in force (ADR-026 §4):
 * {@code SHORTENED}, {@code CATEGORY_WITHDRAWN:<code>} or {@code RENEWED}; null for a change of status.
 */
public record CnsHstRdMdl(String cnsReqId, CnsLcySts fromSts, CnsLcySts toSts, Instant occurredAt, String evtId,
                          String chg) {

    /** A change of status. */
    public CnsHstRdMdl(String cnsReqId, CnsLcySts fromSts, CnsLcySts toSts, Instant occurredAt, String evtId) {
        this(cnsReqId, fromSts, toSts, occurredAt, evtId, null);
    }
}
