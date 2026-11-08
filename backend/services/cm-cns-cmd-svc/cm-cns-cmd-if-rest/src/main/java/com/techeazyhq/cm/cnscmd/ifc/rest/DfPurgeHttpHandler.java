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

import java.time.Instant;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.PurgeDfRecordsHandler;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

/**
 * {@code POST /internal/dfs/{id}/purge?before=<instant>&limit=500}: cm-df-reg-svc deleting a closed fiduciary's records
 * past their retention (ADR-064). Internal, reached on the service network only (the gateway does not route {@code /internal}).
 */
public class DfPurgeHttpHandler {

    private final PurgeDfRecordsHandler purge;

    public DfPurgeHttpHandler(PurgeDfRecordsHandler purge) {
        this.purge = purge;
    }

    public Mono<ServerResponse> purge(ServerRequest request) {
        Instant before;
        try {
            before = Instant.parse(request.queryParam("before").orElse(""));
        } catch (java.time.format.DateTimeParseException e) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "before must be an instant, such as 2019-11-08T00:00:00Z"));
        }
        int limit = request.queryParam("limit").map(Integer::parseInt).orElse(500);
        return purge.handle(request.pathVariable("id"), before, limit).flatMap(done -> ServerResponse.ok().bodyValue(done));
    }
}
