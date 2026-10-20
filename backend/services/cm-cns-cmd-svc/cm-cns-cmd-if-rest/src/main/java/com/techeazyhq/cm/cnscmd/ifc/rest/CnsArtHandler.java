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

import org.springframework.http.CacheControl;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.ChangeCnsArtCmds;
import com.techeazyhq.cm.cnscmd.app.GetCnsArtLiveStatusQryHandler;
import com.techeazyhq.cm.cnscmd.app.RefreshCnsTknCmdHandler;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmd;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmdHandler;
import com.techeazyhq.cm.seckit.AuthCtx;

import reactor.core.publisher.Mono;

/** Functional-endpoint handler (plan §10) for {@code CnsArt} operations. */
public class CnsArtHandler {

    private final RevokeCnsArtCmdHandler revokeHandler;
    private final RefreshCnsTknCmdHandler refreshTknHandler;
    private final GetCnsArtLiveStatusQryHandler liveStatusHandler;
    private final ChangeCnsArtCmds changes;

    public CnsArtHandler(RevokeCnsArtCmdHandler revokeHandler, RefreshCnsTknCmdHandler refreshTknHandler,
                         GetCnsArtLiveStatusQryHandler liveStatusHandler, ChangeCnsArtCmds changes) {
        this.revokeHandler = revokeHandler;
        this.refreshTknHandler = refreshTknHandler;
        this.liveStatusHandler = liveStatusHandler;
        this.changes = changes;
    }

    /** {@code POST .../cns-arts/{id}/shorten} {@code {"until": instant}}: the principal ends it earlier (ADR-026 §4). */
    public Mono<ServerResponse> shorten(ServerRequest request) {
        String id = request.pathVariable("id");
        return CnsReqHandler.asPrincipal(request, ctx -> body(request)
                .flatMap(b -> changes.shorten(id, ctx.dpId(), b.until()))
                .flatMap(art -> ServerResponse.ok().bodyValue(CnsArtRespDto.of(art))));
    }

    /** {@code POST .../cns-arts/{id}/withdraw-dcat} {@code {"dCatCode": ...}}: stops sharing one category. */
    public Mono<ServerResponse> withdrawCategory(ServerRequest request) {
        String id = request.pathVariable("id");
        return CnsReqHandler.asPrincipal(request, ctx -> body(request)
                .flatMap(b -> changes.withdrawCategory(id, ctx.dpId(), b.dCatCode()))
                .flatMap(art -> ServerResponse.ok().bodyValue(CnsArtRespDto.of(art))));
    }

    /** {@code POST .../cns-arts/{id}/renew} {@code {"validityDays": n}}: a new validity from now, before it lapses. */
    public Mono<ServerResponse> renew(ServerRequest request) {
        String id = request.pathVariable("id");
        return CnsReqHandler.asPrincipal(request, ctx -> body(request)
                .flatMap(b -> changes.renew(id, ctx.dpId(), b.validityDays()))
                .flatMap(art -> ServerResponse.ok().bodyValue(CnsArtRespDto.of(art))));
    }

    private static Mono<ChangeReqDto> body(ServerRequest request) {
        return request.bodyToMono(ChangeReqDto.class).defaultIfEmpty(new ChangeReqDto(null, null, null));
    }

    /** One of these, depending on the change. */
    record ChangeReqDto(Instant until, String dCatCode, Integer validityDays) {
    }

    /** {@code POST .../cns-arts/{id}/revoke}: the principal withdraws their consent; no one else can. */
    public Mono<ServerResponse> revoke(ServerRequest request) {
        String id = request.pathVariable("id");
        return CnsReqHandler.asPrincipal(request, ctx -> revokeHandler.handle(new RevokeCnsArtCmd(id, ctx.dpId()))
                .flatMap(cnsArt -> ServerResponse.ok().bodyValue(CnsArtRespDto.of(cnsArt))));
    }

    /** {@code POST /api/cns-cmd/cns-arts/{id}/token}: a fresh short-lived token for the fiduciary the artefact names. */
    public Mono<ServerResponse> refreshToken(ServerRequest request) {
        String id = request.pathVariable("id");
        return CnsReqHandler.authCtx(request)
                .map(AuthCtx::dfId)
                .defaultIfEmpty("")
                .flatMap(dfId -> refreshTknHandler.handle(new RefreshCnsTknCmdHandler.Cmd(id, dfId)))
                .flatMap(issued -> ServerResponse.ok()
                        .cacheControl(CacheControl.noStore())
                        .bodyValue(new TokenRespDto(issued.token(), issued.exp())));
    }

    /** {@code GET /internal/cns-arts/{id}/status}: the PDP's live check, from the write side (ADR-027). */
    public Mono<ServerResponse> liveStatus(ServerRequest request) {
        return liveStatusHandler.handle(request.pathVariable("id"))
                .flatMap(status -> ServerResponse.ok()
                        .cacheControl(CacheControl.noStore())
                        .bodyValue(new StatusRespDto(status.name())))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    record TokenRespDto(String cnsTkn, Instant exp) {
    }

    record StatusRespDto(String status) {
    }
}
