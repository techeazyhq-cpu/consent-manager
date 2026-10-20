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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Consent records and their receipts (ADR-025): implemented by {@code R2dbcCnsRecRepoAdapter}. Records are written
 * as each change is (see {@link CnsRecording}); events and receipts are only ever added.
 */
public interface CnsRecRepoPort {

    /** Writes what one change adds to a record, in the caller's transaction. */
    Mono<Void> record(CnsRecording.Entry entry);

    /** A request's record with every event, oldest first. */
    Mono<CnsRec> findByCnsReqId(String cnsReqId);

    /** Every record whose subject is this principal, newest first. */
    Flux<CnsRec> findBySubject(String dpId);

    /** Record events without a receipt yet, oldest first: {@code (record, seq)}. */
    Flux<Pending> pendingReceipts(int limit);

    record Pending(String cnsReqId, int seq) {
    }

    Mono<CnsRcpt> saveReceipt(CnsRcpt rcpt);

    /** A record's receipts, by event. */
    Flux<CnsRcpt> receipts(String recId);
}
