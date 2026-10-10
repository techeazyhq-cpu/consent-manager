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

package com.techeazyhq.cm.cnscmd.ifc.rest;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.LinkDpCmd;
import com.techeazyhq.cm.cnscmd.app.LinkDpCmdHandler;

import reactor.core.publisher.Mono;

/**
 * Functional-endpoint handler (plan §7a/§10). {@code newDpId} comes from the caller's own
 * JWT {@code sub} — never the request body — so a DP can only ever link records matching
 * their own verified contact info.
 */
public class DpLinkHandler {

    private final LinkDpCmdHandler linkDpHandler;

    public DpLinkHandler(LinkDpCmdHandler linkDpHandler) {
        this.linkDpHandler = linkDpHandler;
    }

    public Mono<ServerResponse> link(ServerRequest request) {
        return request.bodyToMono(DpLinkReqDto.class)
                .zipWith(CnsReqHandler.authCtx(request))
                .flatMap(tuple -> linkDpHandler.handle(
                        new LinkDpCmd(tuple.getT2().subject(), tuple.getT1().extDpRefTyp(), tuple.getT1().extDpRefVal())))
                .flatMap(count -> ServerResponse.ok().bodyValue(new LinkResultDto(count)));
    }

    record LinkResultDto(long linkedCount) {
    }
}
