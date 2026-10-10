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

package com.techeazyhq.cm.aud.app;

import com.techeazyhq.cm.aud.dom.AudChainVerifier;
import com.techeazyhq.cm.aud.dom.AudDraft;
import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudLedgerPort;
import com.techeazyhq.cm.aud.dom.AudQuery;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;

/** The audit ledger's use cases (ADR-014): record an event, look entries up, verify the whole chain. */
public class AudLedgerService {

    private final AudLedgerPort ledger;
    private final Clock clock;

    public AudLedgerService(AudLedgerPort ledger, Clock clock) {
        this.ledger = ledger;
        this.clock = clock;
    }

    public Mono<AudEntry> record(AudDraft draft) {
        return ledger.append(draft, clock.instant());
    }

    public Flux<AudEntry> find(AudQuery query) {
        return ledger.find(query);
    }

    public Mono<AudEntry> head() {
        return ledger.head();
    }

    /** Recomputes every link from the first entry; the answer names the first entry that does not hold. */
    public Mono<AudChainVerifier.Verdict> verify() {
        return Mono.defer(() -> {
            AudChainVerifier verifier = new AudChainVerifier();
            return ledger.records().doOnNext(verifier::accept).then(Mono.fromSupplier(verifier::verdict));
        });
    }
}
