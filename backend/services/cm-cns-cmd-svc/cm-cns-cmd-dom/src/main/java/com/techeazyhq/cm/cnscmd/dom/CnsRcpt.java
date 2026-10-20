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

/**
 * A consent receipt (ISO/IEC TS 27560; ADR-025): the record as it stood after one event, signed with the receipt key
 * (never the consent token key) as a compact JWS. Whoever holds it can prove what was agreed and when, offline, with
 * the published receipt keys. Its hash goes into the audit ledger, whose anchored checkpoints give it trusted time.
 *
 * @param seq    the record event it is for
 * @param jws    the signed receipt; its payload is the receipt's JSON
 * @param sha256 hex SHA-256 of the JWS, as the ledger records it
 */
public record CnsRcpt(String id, String recId, String cnsReqId, int seq, Instant issuedAt, String kid, String jws,
                      String sha256) {
}
