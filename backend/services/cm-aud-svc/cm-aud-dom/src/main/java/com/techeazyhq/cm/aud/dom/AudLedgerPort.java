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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Port: the audit ledger's storage. Implemented by {@code R2dbcAudLedgerAdapter}. */
public interface AudLedgerPort {

    /**
     * Appends the draft as the next link of the chain, atomically with its payload; a draft whose event is already
     * recorded returns the existing entry instead (events arrive at least once).
     */
    Mono<AudEntry> append(AudDraft draft, java.time.Instant recordedAt);

    /** Every entry with its payload, in sequence order, for verification. */
    Flux<AudRecord> records();

    /** Entries matching the query, newest first. */
    Flux<AudEntry> find(AudQuery query);

    /** The last entry, or empty for an empty ledger. */
    Mono<AudEntry> head();
}
