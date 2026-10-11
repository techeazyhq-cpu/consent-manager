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

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnsqry.app.AdminCnsQryHandler;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.seckit.AuthCtx;

import reactor.core.publisher.Mono;

/**
 * {@code /api/cns-qry/admin/dfs/{dfId}/...}, for CM Admin's tenant detail. The gateway already limits these paths to
 * platform staff; this checks the caller's roles again (defense in depth), so a fiduciary or a data principal is
 * refused even if a request reaches the service directly.
 */
public class AdminCnsHandler {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final AdminCnsQryHandler adminQryHandler;

    public AdminCnsHandler(AdminCnsQryHandler adminQryHandler) {
        this.adminQryHandler = adminQryHandler;
    }

    public Mono<ServerResponse> stats(ServerRequest request) {
        String dfId = request.pathVariable("dfId");
        return asStaff(request, () -> adminQryHandler.stats(dfId)
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats)));
    }

    /** Query parameters: {@code sts} (a lifecycle status, all if absent), {@code limit} (1 to 100), {@code offset}. */
    public Mono<ServerResponse> requests(ServerRequest request) {
        String dfId = request.pathVariable("dfId");
        CnsLcySts sts;
        int limit;
        long offset;
        try {
            sts = request.queryParam("sts").filter(s -> !s.isBlank()).map(CnsLcySts::valueOf).orElse(null);
            limit = request.queryParam("limit").map(Integer::parseInt).orElse(DEFAULT_PAGE_SIZE);
            offset = request.queryParam("offset").map(Long::parseLong).orElse(0L);
        } catch (IllegalArgumentException e) {
            return ServerResponse.badRequest().build();
        }
        return asStaff(request, () -> adminQryHandler.requests(dfId, sts, limit, offset)
                .collectList()
                .flatMap(rows -> ServerResponse.ok().bodyValue(rows)));
    }

    private Mono<ServerResponse> asStaff(ServerRequest request, java.util.function.Supplier<Mono<ServerResponse>> action) {
        return request.principal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(token -> AuthCtx.from((Jwt) ((JwtAuthenticationToken) token).getPrincipal()))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(ctx -> ctx.filter(AuthCtx::isPlatformStaff).isPresent()
                        ? action.get()
                        : ServerResponse.status(HttpStatus.FORBIDDEN).build());
    }
}
