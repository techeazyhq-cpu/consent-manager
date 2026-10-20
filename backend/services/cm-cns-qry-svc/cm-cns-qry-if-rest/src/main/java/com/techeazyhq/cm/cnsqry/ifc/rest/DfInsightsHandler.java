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

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnsqry.app.DfInsightsQryHandler;
import com.techeazyhq.cm.seckit.AuthCtx;

import reactor.core.publisher.Mono;

/**
 * {@code GET /api/cns-qry/insights?days=30} (ADR-048): the caller's own fiduciary's outcomes and times to decide, for
 * every member and its API clients. The fiduciary is the token's; anyone else gets 403.
 */
public class DfInsightsHandler {

    private final DfInsightsQryHandler insights;

    public DfInsightsHandler(DfInsightsQryHandler insights) {
        this.insights = insights;
    }

    public Mono<ServerResponse> insights(ServerRequest request) {
        int days;
        try {
            days = request.queryParam("days").map(Integer::parseInt).orElse(DfInsightsQryHandler.DEFAULT_DAYS);
        } catch (NumberFormatException e) {
            return ServerResponse.badRequest().build();
        }
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .filter(AuthCtx::isDf)
                .flatMap(ctx -> insights.handle(ctx.dfId(), days))
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }
}
