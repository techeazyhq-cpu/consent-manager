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

package com.techeazyhq.cm.cnsqry.ifc.rest;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnsqry.app.DpDashboardQryHandler;
import com.techeazyhq.cm.cnsqry.app.GetCnsArtQryHandler;
import com.techeazyhq.cm.cnsqry.app.GetCnsHstQryHandler;
import com.techeazyhq.cm.cnsqry.app.InternalStatusQryHandler;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdl;
import com.techeazyhq.cm.seckit.AuthCtx;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Functional-endpoint handler (plan §10's preference over {@code @RestController}). */
public class CnsQryHandler {

    private final GetCnsArtQryHandler getCnsArtHandler;
    private final GetCnsHstQryHandler getCnsHstHandler;
    private final DpDashboardQryHandler dpDashboardHandler;
    private final InternalStatusQryHandler internalStatusHandler;

    public CnsQryHandler(GetCnsArtQryHandler getCnsArtHandler, GetCnsHstQryHandler getCnsHstHandler,
                          DpDashboardQryHandler dpDashboardHandler, InternalStatusQryHandler internalStatusHandler) {
        this.getCnsArtHandler = getCnsArtHandler;
        this.getCnsHstHandler = getCnsHstHandler;
        this.dpDashboardHandler = dpDashboardHandler;
        this.internalStatusHandler = internalStatusHandler;
    }

    /**
     * {@code dpId}/{@code dfId} are derived from the caller's own JWT (plan §7a's {@link AuthCtx}),
     * never trusted as client-supplied query params — otherwise any authenticated DP or DF could
     * pass a different party's id and read their read-model rows (data-blind, but still a
     * cross-tenant metadata leak). A CM_ADMIN caller has neither, so gets an empty result here;
     * cross-DF/DP admin visibility is a Phase-2 concern, not this endpoint's job.
     */
    public Mono<ServerResponse> listCnsArts(ServerRequest request) {
        return authCtx(request).flatMapMany(ctx -> {
            if (ctx.isDp()) {
                return getCnsArtHandler.byDpId(ctx.dpId());
            }
            if (ctx.isDf()) {
                return getCnsArtHandler.byDfId(ctx.dfId());
            }
            return Flux.<CnsArtRdMdl>empty();
        }).collectList().flatMap(rows -> ServerResponse.ok().bodyValue(rows));
    }

    public Mono<ServerResponse> getCnsArt(ServerRequest request) {
        return getCnsArtHandler.byId(request.pathVariable("id"))
                .flatMap(row -> ServerResponse.ok().bodyValue(row))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> getCnsHst(ServerRequest request) {
        return ServerResponse.ok().body(getCnsHstHandler.handle(request.pathVariable("id")), CnsHstRdMdl.class);
    }

    public Mono<ServerResponse> dpDashboard(ServerRequest request) {
        return authCtx(request)
                .flatMapMany(ctx -> dpDashboardHandler.handle(ctx.dpId()))
                .collectList()
                .flatMap(rows -> ServerResponse.ok().bodyValue(rows));
    }

    public Mono<ServerResponse> internalStatus(ServerRequest request) {
        return internalStatusHandler.handle(request.pathVariable("id"))
                .flatMap(status -> ServerResponse.ok().bodyValue(new StatusRespDto(status.name())));
    }

    /**
     * Reached only after {@code cm-gw}'s PEP already got an ALLOW from OPA (plan §9) — this
     * handler's job is just to confirm that, data-blind (no DP data, just a verdict echo).
     * Placeholder note: without the gateway's PEP filter in front of it, calling this route
     * directly would bypass Stage 3 enforcement entirely — it is deliberately not itself a
     * re-check.
     */
    public Mono<ServerResponse> verify(ServerRequest request) {
        return ServerResponse.ok().bodyValue(new VerifyRespDto("ALLOW"));
    }

    private Mono<AuthCtx> authCtx(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class).map(token -> AuthCtx.from((Jwt) token.getPrincipal()));
    }

    record StatusRespDto(String status) {
    }

    record VerifyRespDto(String result) {
    }
}
