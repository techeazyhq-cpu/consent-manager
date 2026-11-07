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

import com.techeazyhq.cm.dfreg.app.DfRetentionHandler;
import com.techeazyhq.cm.dfreg.dom.Df;

import reactor.core.publisher.Mono;

/** {@code PUT} (with a reason) and {@code DELETE /api/df-reg/dfs/{id}/legal-hold}: an administrator holds or releases a fiduciary's records (ADR-064). */
public class DfLegalHoldHttpHandler {

    private final DfRetentionHandler retention;

    public DfLegalHoldHttpHandler(DfRetentionHandler retention) {
        this.retention = retention;
    }

    public Mono<ServerResponse> place(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(HoldReqDto.class).defaultIfEmpty(new HoldReqDto(null))
                .flatMap(body -> retention.placeLegalHold(id, body.reason()))
                .flatMap(df -> ServerResponse.ok().bodyValue(HoldDto.of(df)));
    }

    public Mono<ServerResponse> release(ServerRequest request) {
        return retention.releaseLegalHold(request.pathVariable("id")).flatMap(df -> ServerResponse.ok().bodyValue(HoldDto.of(df)));
    }

    record HoldReqDto(String reason) {
    }

    record HoldDto(String id, String legalHoldReason, Instant legalHoldAt) {

        static HoldDto of(Df df) {
            return new HoldDto(df.id(), df.legalHoldReason(), df.legalHoldAt());
        }
    }
}
