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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.CnsRecords;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Consent records and receipts (ADR-025), for the principal a record names and the fiduciary that is its controller.
 * <ul>
 *   <li>{@code GET /api/cns-cmd/records/{cnsReqId}}: the record as an ISO/IEC TS 27560 document, JSON-LD.</li>
 *   <li>{@code GET .../records/{cnsReqId}/receipts}: its receipts, each a compact JWS signed with a receipt key.</li>
 *   <li>{@code GET /api/cns-cmd/records/export}: the principal's records and receipts, all of them, as one file.</li>
 * </ul>
 */
public class CnsRecHttpHandler {

    static final MediaType JSON_LD = MediaType.parseMediaType("application/ld+json");

    private final CnsRecords records;

    public CnsRecHttpHandler(CnsRecords records) {
        this.records = records;
    }

    public Mono<ServerResponse> record(ServerRequest request) {
        return CnsReqHandler.authCtx(request)
                .flatMap(ctx -> records.recordFor(request.pathVariable("cnsReqId"), ctx.dpId(), ctx.dfId()))
                .flatMap(records::document)
                .flatMap(doc -> ServerResponse.ok().contentType(JSON_LD).cacheControl(CacheControl.noStore()).bodyValue(doc));
    }

    public Mono<ServerResponse> receipts(ServerRequest request) {
        return CnsReqHandler.authCtx(request)
                .flatMap(ctx -> records.recordFor(request.pathVariable("cnsReqId"), ctx.dpId(), ctx.dfId()))
                .flatMap(rec -> records.receipts(rec).map(CnsRecords::receiptView).collectList())
                .flatMap(list -> ServerResponse.ok().cacheControl(CacheControl.noStore()).bodyValue(list));
    }

    /** The principal's own export only: a fiduciary reads records one by one, for the consents it asked for. */
    public Mono<ServerResponse> export(ServerRequest request) {
        return CnsReqHandler.authCtx(request)
                .filter(AuthCtx::isDp)
                .switchIfEmpty(Mono.error(() -> new DomRuleViolation(403, "principal-only",
                        "Only a person exports their own consent records")))
                .flatMap(ctx -> records.export(ctx.dpId()))
                .flatMap(export -> ServerResponse.status(HttpStatus.OK)
                        .contentType(JSON_LD)
                        .cacheControl(CacheControl.noStore())
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.attachment().filename("my-consent-records.json").build().toString())
                        .bodyValue(export));
    }
}
