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


package com.techeazyhq.cm.dfreg.dom;

import java.time.Instant;

import reactor.core.publisher.Mono;

public interface DfSignupRepoPort {

    Mono<DfSignup> save(DfSignup signup);

    Mono<DfSignup> findById(String id);

    /** Applications for this address (as stored, lower case) since {@code since}. */
    Mono<Long> countByEmailSince(String contactEmail, Instant since);

    /** All applications since {@code since}. */
    Mono<Long> countSince(Instant since);

    /** Removes applications never confirmed whose token expired before {@code before}: they hold an address nobody confirmed. */
    Mono<Long> deleteUnconfirmedExpiredBefore(Instant before);
}
