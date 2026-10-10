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

import com.techeazyhq.cm.aud.app.AudLedgerService;
import com.techeazyhq.cm.aud.dom.AudChain;
import com.techeazyhq.cm.aud.dom.AudQuery;
import com.techeazyhq.cm.seckit.AuthCtx;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * {@code /api/aud/**}: reading and verifying the audit ledger (ADR-014). Who sees what comes from the caller's own JWT,
 * never from a parameter: an administrator may look up any entry and verify the whole chain; a fiduciary sees the
 * entries about itself; a principal sees the entries about them. Anyone signed in may read the chain head, so outsiders
 * can note it and later check that the history up to it has not changed.
 */
public class AudHandler {

    private final AudLedgerService ledger;

    public AudHandler(AudLedgerService ledger) {
        this.ledger = ledger;
    }

    public Mono<ServerResponse> entries(ServerRequest request) {
        return authCtx(request).flatMap(ctx -> {
            String cnsReqId = request.queryParam("cnsReqId").orElse(null);
            String cnsArtId = request.queryParam("cnsArtId").orElse(null);
            int limit = request.queryParam("limit").map(AudHandler::parseLimit).orElse(100);
            AudQuery query;
            if (ctx.isCmAdmin()) {
                query = new AudQuery(cnsReqId, cnsArtId, request.queryParam("dfId").orElse(null), null, limit);
            } else if (ctx.isDf()) {
                query = new AudQuery(cnsReqId, cnsArtId, ctx.dfId(), null, limit);
            } else if (ctx.isDp()) {
                query = new AudQuery(cnsReqId, cnsArtId, null, ctx.dpId(), limit);
            } else {
                return forbidden();
            }
            return ledger.find(query).collectList().flatMap(entries -> ServerResponse.ok().bodyValue(entries));
        });
    }

    public Mono<ServerResponse> head(ServerRequest request) {
        return ledger.head()
                .map(entry -> Map.<String, Object>of("seq", entry.seq(), "entryHash", entry.entryHash()))
                .defaultIfEmpty(Map.of("seq", 0L, "entryHash", AudChain.GENESIS_HASH))
                .flatMap(head -> ServerResponse.ok().bodyValue(head));
    }

    public Mono<ServerResponse> verify(ServerRequest request) {
        return authCtx(request).flatMap(ctx -> ctx.isCmAdmin()
                ? ledger.verify().flatMap(verdict -> ServerResponse.ok().bodyValue(verdict))
                : forbidden());
    }

    private static int parseLimit(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private static Mono<AuthCtx> authCtx(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class).map(token -> AuthCtx.from((Jwt) token.getPrincipal()));
    }

    private static Mono<ServerResponse> forbidden() {
        return ServerResponse.status(403).bodyValue(Map.of("type", "urn:cm:problem:aud-forbidden", "title", "aud-forbidden"));
    }
}
