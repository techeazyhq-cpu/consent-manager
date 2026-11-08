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

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.StatusLists;

import reactor.core.publisher.Mono;

/**
 * {@code GET /api/cns-cmd/status-lists/{dfId}}: a fiduciary's status list as a signed token (ADR-077), open to anyone, since
 * it holds one bit per consent token and no name. A verifier may use it for the {@code ttl} it states; the answer also says
 * so in {@code Cache-Control}, at half that, so a cache never holds it for the whole of its life.
 */
public class StatusListHttpHandler {

    public static final MediaType STATUS_LIST_JWT = MediaType.parseMediaType("application/statuslist+jwt");

    private final StatusLists lists;

    public StatusListHttpHandler(StatusLists lists) {
        this.lists = lists;
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        return lists.tokenFor(request.pathVariable("dfId"))
                .flatMap(token -> ServerResponse.ok().contentType(STATUS_LIST_JWT)
                        .cacheControl(CacheControl.maxAge(lists.ttl().dividedBy(2)).cachePublic())
                        .bodyValue(token));
    }
}
