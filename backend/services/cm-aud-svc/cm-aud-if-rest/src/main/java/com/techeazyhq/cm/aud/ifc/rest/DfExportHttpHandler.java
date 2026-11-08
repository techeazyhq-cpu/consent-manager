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


package com.techeazyhq.cm.aud.ifc.rest;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.aud.app.AudLedgerService;

import reactor.core.publisher.Mono;

/**
 * {@code GET /internal/dfs/{id}/export?limit=5000}: cm-df-reg-svc assembling a fiduciary's own data export (ADR-064).
 * Internal, reached on the service network only (the gateway does not route {@code /internal}).
 */
public class DfExportHttpHandler {

    private final AudLedgerService ledger;

    public DfExportHttpHandler(AudLedgerService ledger) {
        this.ledger = ledger;
    }

    public Mono<ServerResponse> export(ServerRequest request) {
        int limit = request.queryParam("limit").map(Integer::parseInt).orElse(5_000);
        return ledger.exportForDf(request.pathVariable("id"), limit).flatMap(out -> ServerResponse.ok().bodyValue(out));
    }
}
