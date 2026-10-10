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

/** Port (plan §12, Repository pattern): implemented by {@code R2dbcCnsReqRepoAdapter} in -infra. */
public interface CnsReqRepoPort {

    Mono<CnsReq> save(CnsReq cnsReq);

    Mono<CnsReq> findById(String id);

    /** Every unlinked CnsReq matching a given external contact — used by LinkDpCmdHandler (plan §7a). */
    Flux<CnsReq> findUnlinkedByExtRef(ExtDpRefTyp typ, String val);
}
