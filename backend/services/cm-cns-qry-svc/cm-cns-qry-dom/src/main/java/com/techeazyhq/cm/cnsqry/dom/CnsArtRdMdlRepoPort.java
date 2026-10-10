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

package com.techeazyhq.cm.cnsqry.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CnsArtRdMdlRepoPort {

    Mono<CnsArtRdMdl> upsert(CnsArtRdMdl row);

    Mono<CnsArtRdMdl> findByCnsReqId(String cnsReqId);

    Mono<CnsArtRdMdl> findByCnsArtId(String cnsArtId);

    Flux<CnsArtRdMdl> findByDpId(String dpId);

    Flux<CnsArtRdMdl> findByDfId(String dfId);

    /** Sets dp_id on every row still null-dp matching (cnsReqId/cnsArtId) — used projecting CnsDpLinkedEvt (plan §7a). */
    Mono<Void> linkDp(String recordId, String recordType, String newDpId);

    Flux<DpDashboardRow> dashboardFor(String dpId);
}
