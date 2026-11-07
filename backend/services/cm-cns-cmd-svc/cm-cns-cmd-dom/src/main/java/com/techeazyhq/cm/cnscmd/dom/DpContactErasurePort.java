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

import java.util.List;

import reactor.core.publisher.Mono;

/**
 * Erases the contact details (email address or phone number) the Consent context still holds for a principal's
 * requests and consents, when they close their account (ADR-059): in the lists a fiduciary sent, and in published
 * outbox events. The requests and consents keep the principal's pseudonymous id, and their records stay.
 */
public interface DpContactErasurePort {

    /** How many rows it changed. Running it again changes nothing. */
    Mono<Long> eraseContacts(List<String> cnsReqIds, List<String> cnsArtIds);

    /** The same for everything a fiduciary that has closed ever sent (ADR-064). Running it again changes nothing. */
    default Mono<Long> eraseContactsOfDf(String dfId) {
        return Mono.error(new UnsupportedOperationException("eraseContactsOfDf"));
    }
}
