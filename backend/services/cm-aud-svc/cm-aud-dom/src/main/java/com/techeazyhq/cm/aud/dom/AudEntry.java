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
import java.util.UUID;

/**
 * One link of the audit ledger's hash chain (ADR-014). {@code entryHash} is the SHA-256 of this entry's fields and
 * {@code prevHash}, the previous entry's hash, so changing, removing or reordering any recorded entry changes every
 * hash after it. {@code payloadSha256} commits to the recorded payload, which is stored separately and may be erased.
 */
public record AudEntry(long seq, UUID evtId, String evtType, Instant occurredAt, Instant recordedAt, String cnsReqId,
                       String cnsArtId, String dfId, String payloadSha256, String prevHash, String entryHash) {
}
