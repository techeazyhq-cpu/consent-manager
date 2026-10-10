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

package com.techeazyhq.cm.cnsqry.app;

import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GetCnsArtQryHandler {

    private final CnsArtRdMdlRepoPort rdMdlRepo;

    public GetCnsArtQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        this.rdMdlRepo = rdMdlRepo;
    }

    public Mono<CnsArtRdMdl> byId(String id) {
        return rdMdlRepo.findByCnsArtId(id).switchIfEmpty(rdMdlRepo.findByCnsReqId(id));
    }

    public Flux<CnsArtRdMdl> byDpId(String dpId) {
        return rdMdlRepo.findByDpId(dpId);
    }

    public Flux<CnsArtRdMdl> byDfId(String dfId) {
        return rdMdlRepo.findByDfId(dfId);
    }
}
