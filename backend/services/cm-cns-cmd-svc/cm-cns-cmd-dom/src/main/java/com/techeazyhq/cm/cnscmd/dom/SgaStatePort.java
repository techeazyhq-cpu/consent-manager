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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Saga progress in {@code cns_cmd.sga_state} (ADR-005): implemented by {@code R2dbcSgaStateAdapter}. */
public interface SgaStatePort {

    /** Records nothing: for tests and callers that keep no saga state. */
    SgaStatePort NONE = new SgaStatePort() {
        @Override
        public Mono<Void> started(SgaStep step, Instant at) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> done(String sagaId, Instant at) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> failed(String sagaId, String error, Instant at) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> gaveUp(String sagaId, Instant at) {
            return Mono.empty();
        }

        @Override
        public Flux<SgaStep> findUnfinishedBefore(Instant before, int limit) {
            return Flux.empty();
        }

        @Override
        public Mono<SgaStep> findById(String sagaId) {
            return Mono.empty();
        }
    };

    /** The leg is running: a new row, or the existing one back to STARTED with one more attempt. */
    Mono<Void> started(SgaStep step, Instant at);

    Mono<Void> done(String sagaId, Instant at);

    Mono<Void> failed(String sagaId, String error, Instant at);

    /** The sweep stopped retrying; the row stays for an operator to see. */
    Mono<Void> gaveUp(String sagaId, Instant at);

    /** STARTED or FAILED legs last touched before {@code before}, oldest first. */
    Flux<SgaStep> findUnfinishedBefore(Instant before, int limit);

    Mono<SgaStep> findById(String sagaId);
}
