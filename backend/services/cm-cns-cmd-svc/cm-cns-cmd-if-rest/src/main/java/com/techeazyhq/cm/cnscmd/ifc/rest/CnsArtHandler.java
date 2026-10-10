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

import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmd;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmdHandler;

import reactor.core.publisher.Mono;

/** Functional-endpoint handler (plan §10) for {@code CnsArt} operations. */
public class CnsArtHandler {

    private final RevokeCnsArtCmdHandler revokeHandler;

    public CnsArtHandler(RevokeCnsArtCmdHandler revokeHandler) {
        this.revokeHandler = revokeHandler;
    }

    public Mono<ServerResponse> revoke(ServerRequest request) {
        String id = request.pathVariable("id");
        return revokeHandler.handle(new RevokeCnsArtCmd(id))
                .flatMap(cnsArt -> ServerResponse.ok().bodyValue(CnsArtRespDto.of(cnsArt)));
    }
}
