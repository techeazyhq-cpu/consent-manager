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


package com.techeazyhq.cm.dfreg.ifc.rest;

import java.time.Instant;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.DfSuspensionHandler;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfSts;

import reactor.core.publisher.Mono;

/** {@code POST /api/df-reg/dfs/{id}/suspend} (with a reason) and {@code /resume}: platform staff only (ADR-064). */
public class DfSuspensionHttpHandler {

    private final DfSuspensionHandler suspensions;

    public DfSuspensionHttpHandler(DfSuspensionHandler suspensions) {
        this.suspensions = suspensions;
    }

    public Mono<ServerResponse> suspend(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(SuspendReqDto.class).defaultIfEmpty(new SuspendReqDto(null))
                .flatMap(body -> suspensions.suspend(id, body.reason()))
                .flatMap(df -> ServerResponse.ok().bodyValue(StatusDto.of(df)));
    }

    public Mono<ServerResponse> resume(ServerRequest request) {
        return suspensions.resume(request.pathVariable("id")).flatMap(df -> ServerResponse.ok().bodyValue(StatusDto.of(df)));
    }

    record SuspendReqDto(String reason) {
    }

    record StatusDto(String id, DfSts sts, Instant suspendedAt, String suspensionReason) {

        static StatusDto of(Df df) {
            return new StatusDto(df.id(), df.sts(), df.suspendedAt(), df.suspensionReason());
        }
    }
}
