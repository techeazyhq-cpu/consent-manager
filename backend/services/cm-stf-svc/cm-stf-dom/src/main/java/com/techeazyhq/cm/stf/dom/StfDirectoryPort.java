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

package com.techeazyhq.cm.stf.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Platform staff accounts where they live, in the identity provider (Keycloak). */
public interface StfDirectoryPort {

    /** Creates an enabled account with the {@link StfRole#CM_OPERATOR} role; {@link StfAcctExistsException} if taken. */
    Mono<StfAcct> createOperator(NewOperator operator);

    /** Everyone with a staff role, admins and operators. */
    Flux<StfAcct> listStaff();

    /** Empty if there is no such account, or it has no staff role. */
    Mono<StfAcct> findStaff(String id);

    /** Disabling also ends every session the account has, so it is signed out at once. */
    Mono<Void> setEnabled(String id, boolean enabled);

    Flux<StfSession> sessionsOf(String id);

    Mono<Void> signOutEverywhere(String id);
}
