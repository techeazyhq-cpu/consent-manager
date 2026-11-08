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


package com.techeazyhq.cm.ntf.dom;

import reactor.core.publisher.Mono;

public interface DpPrefRepoPort {

    /** Nothing stored, nothing changed: what a service with no preferences table uses. */
    DpPrefRepoPort NONE = new DpPrefRepoPort() {
        @Override
        public Mono<DpPref> find(String dpId) {
            return Mono.empty();
        }

        @Override
        public Mono<DpPref> findByEmailHash(String emailHash) {
            return Mono.empty();
        }

        @Override
        public Mono<DpPref> save(DpPref pref) {
            return Mono.just(pref);
        }

        @Override
        public Mono<Long> deleteOf(String dpId) {
            return Mono.just(0L);
        }
    };

    /** Empty when the principal never chose. */
    Mono<DpPref> find(String dpId);

    /** The principal whose address hashes to this, if one chose anything and signed in with an email address. */
    Mono<DpPref> findByEmailHash(String emailHash);

    Mono<DpPref> save(DpPref pref);

    /** Erases what is held for a principal who closed their account (ADR-059); how many rows. */
    Mono<Long> deleteOf(String dpId);
}
