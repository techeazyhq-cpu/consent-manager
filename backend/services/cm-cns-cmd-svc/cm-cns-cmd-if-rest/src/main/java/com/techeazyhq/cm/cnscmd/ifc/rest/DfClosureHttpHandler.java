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

import com.techeazyhq.cm.cnscmd.app.CloseDfConsentsHandler;

import reactor.core.publisher.Mono;

/**
 * {@code POST /internal/dfs/{id}/close-consents?limit=200}: cm-df-reg-svc closing a fiduciary (ADR-064). Internal, reached
 * on the service network only (the gateway does not route {@code /internal}).
 */
public class DfClosureHttpHandler {

    private final CloseDfConsentsHandler closer;

    public DfClosureHttpHandler(CloseDfConsentsHandler closer) {
        this.closer = closer;
    }

    public Mono<ServerResponse> closeConsents(ServerRequest request) {
        int limit = request.queryParam("limit").map(Integer::parseInt).orElse(200);
        return closer.handle(request.pathVariable("id"), limit).flatMap(result -> ServerResponse.ok().bodyValue(result));
    }
}
