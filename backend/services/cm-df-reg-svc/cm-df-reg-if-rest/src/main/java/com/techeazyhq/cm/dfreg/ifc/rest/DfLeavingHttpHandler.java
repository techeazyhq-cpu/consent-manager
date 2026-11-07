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

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.DfLeavingHandler;
import com.techeazyhq.cm.dfreg.app.DfTeamCaller;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * The signed-in member's own fiduciary leaving (ADR-064): {@code GET /api/df-reg/me/leave} for anyone on the team,
 * {@code POST} to ask (the Owner, typing the organisation's name) and {@code POST .../cancel} to call it off once.
 */
public class DfLeavingHttpHandler {

    private final DfLeavingHandler leaving;

    public DfLeavingHttpHandler(DfLeavingHandler leaving) {
        this.leaving = leaving;
    }

    public Mono<ServerResponse> status(ServerRequest request) {
        return caller(request).flatMap(leaving::status).flatMap(df -> ServerResponse.ok().bodyValue(StatusDto.of(df)));
    }

    public Mono<ServerResponse> leave(ServerRequest request) {
        return caller(request)
                .zipWith(request.bodyToMono(LeaveReqDto.class).defaultIfEmpty(new LeaveReqDto(null)))
                .flatMap(t -> leaving.leave(t.getT1(), t.getT2().organisationName()))
                .flatMap(df -> ServerResponse.ok().bodyValue(StatusDto.of(df)));
    }

    public Mono<ServerResponse> cancel(ServerRequest request) {
        return caller(request).flatMap(leaving::cancel).flatMap(df -> ServerResponse.ok().bodyValue(StatusDto.of(df)));
    }

    private static Mono<DfTeamCaller> caller(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .flatMap(ctx -> ctx.isDf()
                        ? DfRole.fromRoles(ctx.roles()).map(role -> Mono.just(DfTeamCaller.member(ctx.subject(), ctx.dfId(), role)))
                                .orElseGet(() -> Mono.error(new DomRuleViolation(403, "not-permitted", "Only people at the fiduciary")))
                        : Mono.error(new DomRuleViolation(403, "not-permitted", "Only people at the fiduciary")));
    }

    record LeaveReqDto(String organisationName) {
    }

    record StatusDto(String id, DfSts sts, Instant leavingAt, Instant leavesAt, boolean canCancel) {

        static StatusDto of(Df df) {
            return new StatusDto(df.id(), df.sts(), df.leavingAt(), df.leavesAt(), df.canCancelLeaving());
        }
    }
}
