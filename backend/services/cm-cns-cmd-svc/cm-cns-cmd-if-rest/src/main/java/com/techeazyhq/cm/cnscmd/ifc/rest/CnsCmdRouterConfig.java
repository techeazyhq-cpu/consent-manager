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

import org.springframework.context.annotation.Bean;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.CnsReqBatchQueries;
import com.techeazyhq.cm.cnscmd.app.SubmitCnsReqBatchHandler;
import com.techeazyhq.cm.cnscmd.app.ApproveCnsCmdHandler;
import com.techeazyhq.cm.cnscmd.app.CreateCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.DenyCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.GetCnsArtLiveStatusQryHandler;
import com.techeazyhq.cm.cnscmd.app.ChangeCnsArtCmds;
import com.techeazyhq.cm.cnscmd.app.CnsRecords;
import com.techeazyhq.cm.cnscmd.app.Webhooks;
import com.techeazyhq.cm.cnscmd.dom.RcptSignerPort;
import com.techeazyhq.cm.cnscmd.app.CnsReqTerms;
import com.techeazyhq.cm.cnscmd.app.LinkDpCmdHandler;
import com.techeazyhq.cm.cnscmd.app.PrpsDefCmds;
import com.techeazyhq.cm.cnscmd.app.RefreshCnsTknCmdHandler;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmdHandler;
import com.techeazyhq.cm.seckit.CnsTknKeys;

import java.time.Duration;

/**
 * The service's only route table (plan §10, §13) — functional endpoints (WebFlux
 * {@code RouterFunction}/{@code HandlerFunction}) rather than {@code @RestController}
 * annotations, per project preference. Handler instances are plain objects, not Spring
 * beans, composed here from the {@code -app} command handlers that are beans.
 */
@Configuration
public class CnsCmdRouterConfig {

    /** Public keys that verify consent tokens (ADR-006); unauthenticated, for OPA and for fiduciaries. */
    public static final String JWKS_PATH = "/api/cns-cmd/.well-known/jwks.json";

    /** Public keys that verify consent receipts (ADR-025), retired ones included; unauthenticated, for anyone. */
    public static final String RCPT_JWKS_PATH = "/api/cns-cmd/.well-known/receipt-jwks.json";

    /** The PDP's live status check (ADR-027); internal, reached on the service network only, never through cm-gw. */
    public static final String INTERNAL_PATHS = "/internal/**";

    @Bean
    public RouterFunction<ServerResponse> cnsCmdRoutes(CreateCnsReqCmdHandler createHandler,
                                                         ApproveCnsCmdHandler approveHandler,
                                                         DenyCnsReqCmdHandler denyHandler,
                                                         RevokeCnsArtCmdHandler revokeHandler,
                                                         LinkDpCmdHandler linkDpHandler,
                                                         RefreshCnsTknCmdHandler refreshTknHandler,
                                                         GetCnsArtLiveStatusQryHandler liveStatusHandler,
                                                         CnsTknKeys cnsTknKeys,
                                                         SubmitCnsReqBatchHandler submitBatchHandler,
                                                         CnsReqBatchQueries batchQueries,
                                                         PrpsDefCmds prpsDefCmds,
                                                         CnsReqTerms cnsReqTerms,
                                                         ChangeCnsArtCmds changeCnsArtCmds,
                                                         CnsRecords cnsRecords,
                                                         RcptSignerPort rcptSigner,
                                                         Webhooks webhooks) {
        CnsReqHandler cnsReqHandler = new CnsReqHandler(createHandler, approveHandler, denyHandler, cnsReqTerms);
        CnsArtHandler cnsArtHandler = new CnsArtHandler(revokeHandler, refreshTknHandler, liveStatusHandler,
                changeCnsArtCmds);
        DpLinkHandler dpLinkHandler = new DpLinkHandler(linkDpHandler);
        CnsReqBatchHttpHandler batches = new CnsReqBatchHttpHandler(submitBatchHandler, batchQueries);
        PrpsDefHttpHandler purposes = new PrpsDefHttpHandler(prpsDefCmds);
        CnsRecHttpHandler recs = new CnsRecHttpHandler(cnsRecords);
        WebhookHttpHandler whk = new WebhookHttpHandler(webhooks);

        return RouterFunctions.route()
                // purposes and their versions (ADR-031): what requests may ask for
                .GET("/api/cns-cmd/prps-defs", purposes::list)
                .POST("/api/cns-cmd/prps-defs", purposes::create)
                .GET("/api/cns-cmd/prps-defs/{code}", purposes::get)
                .PUT("/api/cns-cmd/prps-defs/{code}/draft", purposes::saveDraft)
                .DELETE("/api/cns-cmd/prps-defs/{code}/draft", purposes::discardDraft)
                .GET("/api/cns-cmd/prps-defs/{code}/draft/change", purposes::draftChange)
                .POST("/api/cns-cmd/prps-defs/{code}/publish", purposes::publish)
                // fiduciary webhooks (ADR-047)
                .GET("/api/cns-cmd/webhooks/types", whk::types)
                .GET("/api/cns-cmd/webhooks/endpoints", whk::list)
                .POST("/api/cns-cmd/webhooks/endpoints", whk::create)
                .GET("/api/cns-cmd/webhooks/endpoints/{id}", whk::get)
                .DELETE("/api/cns-cmd/webhooks/endpoints/{id}", whk::delete)
                .POST("/api/cns-cmd/webhooks/endpoints/{id}/rotate-secret", whk::rotate)
                .POST("/api/cns-cmd/webhooks/endpoints/{id}/enable", whk::enable)
                .POST("/api/cns-cmd/webhooks/endpoints/{id}/disable", whk::disable)
                .POST("/api/cns-cmd/webhooks/endpoints/{id}/test", whk::test)
                .GET("/api/cns-cmd/webhooks/deliveries", whk::deliveries)
                .GET("/api/cns-cmd/webhooks/deliveries/{id}", whk::delivery)
                .POST("/api/cns-cmd/webhooks/deliveries/{id}/replay", whk::replay)
                // ISO/IEC TS 27560 records and signed receipts (ADR-025)
                .GET("/api/cns-cmd/records/export", recs::export)
                .GET("/api/cns-cmd/records/{cnsReqId}", recs::record)
                .GET("/api/cns-cmd/records/{cnsReqId}/receipts", recs::receipts)
                .GET(RCPT_JWKS_PATH, request -> ServerResponse.ok()
                        .contentType(MediaType.parseMediaType("application/jwk-set+json"))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                        .bodyValue(rcptSigner.publicJwksJson()))
                .POST("/api/cns-cmd/cns-reqs", cnsReqHandler::create)
                // batches (ADR-036): one template, many people, sent in the background
                .POST("/api/cns-cmd/cns-req-batches", batches::create)
                .GET("/api/cns-cmd/cns-req-batches", batches::list)
                .GET("/api/cns-cmd/cns-req-batches/{id}", batches::get)
                .GET("/api/cns-cmd/cns-req-batches/{id}/rows", batches::rows)
                .GET("/api/cns-cmd/cns-req-batches/{id}/report.csv", batches::report)
                .GET("/api/cns-cmd/cns-reqs/{id}/terms", cnsReqHandler::terms)
                .POST("/api/cns-cmd/cns-reqs/{id}/approve", cnsReqHandler::approve)
                .POST("/api/cns-cmd/cns-reqs/{id}/deny", cnsReqHandler::deny)
                .POST("/api/cns-cmd/cns-arts/{id}/revoke", cnsArtHandler::revoke)
                // the principal's changes to a consent they gave (ADR-026 §4)
                .POST("/api/cns-cmd/cns-arts/{id}/shorten", cnsArtHandler::shorten)
                .POST("/api/cns-cmd/cns-arts/{id}/withdraw-dcat", cnsArtHandler::withdrawCategory)
                .POST("/api/cns-cmd/cns-arts/{id}/renew", cnsArtHandler::renew)
                .POST("/api/cns-cmd/cns-arts/{id}/token", cnsArtHandler::refreshToken)
                .GET("/internal/cns-arts/{id}/status", cnsArtHandler::liveStatus)
                .POST("/api/cns-cmd/dp-links", dpLinkHandler::link)
                .GET(JWKS_PATH, request -> ServerResponse.ok()
                        .contentType(MediaType.parseMediaType("application/jwk-set+json"))
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)))
                        .bodyValue(cnsTknKeys.publicJwksJson()))
                .build();
    }
}
