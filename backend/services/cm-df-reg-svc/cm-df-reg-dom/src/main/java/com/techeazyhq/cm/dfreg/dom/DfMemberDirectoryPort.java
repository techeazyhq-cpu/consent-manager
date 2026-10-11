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

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** A fiduciary's people where they live, in the identity provider (Keycloak). */
public interface DfMemberDirectoryPort {

    /** Creates an account for the person, belonging to {@code dfId} with the role; {@link DfMemberExistsException} if taken. */
    Mono<DfMember> invite(String dfId, NewDfMember member);

    Flux<DfMember> list(String dfId);

    /** Empty if there is no such member of {@code dfId}. */
    Mono<DfMember> find(String dfId, String memberId);

    Mono<DfMember> changeRole(DfMember member, DfRole role);

    /** Deletes the account, which also ends its sessions. Its id stays in the audit ledger. */
    Mono<Void> remove(DfMember member);
}
