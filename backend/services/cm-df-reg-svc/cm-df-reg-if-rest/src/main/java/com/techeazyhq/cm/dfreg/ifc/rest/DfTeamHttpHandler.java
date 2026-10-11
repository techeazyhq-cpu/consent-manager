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

import java.util.Locale;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.DfTeamCaller;
import com.techeazyhq.cm.dfreg.app.DfTeamHandler;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * A fiduciary's team (ADR-033). {@code /api/df-reg/me/members} is the signed-in member's own fiduciary, from the
 * {@code df_id} and {@code DF_*} role in their token: an API client's token has no member role, so it can't manage
 * people. {@code POST /api/df-reg/dfs/{id}/members} is platform staff inviting a fiduciary's first Owner.
 */
public class DfTeamHttpHandler {

    private final DfTeamHandler team;

    public DfTeamHttpHandler(DfTeamHandler team) {
        this.team = team;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return asMember(request, caller -> team.list(caller, caller.dfId()).map(MemberRespDto::of).collectList()
                .flatMap(members -> ServerResponse.ok().bodyValue(members)));
    }

    public Mono<ServerResponse> invite(ServerRequest request) {
        return asMember(request, caller -> invite(request, caller, caller.dfId()));
    }

    public Mono<ServerResponse> inviteOwner(ServerRequest request) {
        return asCaller(request, ctx -> ctx.isPlatformStaff() ? DfTeamCaller.staff(ctx.subject()) : null,
                caller -> invite(request, caller, request.pathVariable("id")));
    }

    public Mono<ServerResponse> changeRole(ServerRequest request) {
        String memberId = request.pathVariable("memberId");
        return asMember(request, caller -> request.bodyToMono(RoleReqDto.class)
                .defaultIfEmpty(new RoleReqDto(null))
                .flatMap(body -> team.changeRole(caller, caller.dfId(), memberId, role(body.role())))
                .flatMap(member -> ServerResponse.ok().bodyValue(MemberRespDto.of(member))));
    }

    public Mono<ServerResponse> remove(ServerRequest request) {
        String memberId = request.pathVariable("memberId");
        return asMember(request, caller -> team.remove(caller, caller.dfId(), memberId)
                .then(ServerResponse.noContent().build()));
    }

    private Mono<ServerResponse> invite(ServerRequest request, DfTeamCaller caller, String dfId) {
        return request.bodyToMono(InviteReqDto.class)
                .defaultIfEmpty(new InviteReqDto(null, null, null))
                .flatMap(body -> team.invite(caller, dfId, body.email(), body.name(), role(body.role())))
                .flatMap(member -> ServerResponse.status(HttpStatus.CREATED).bodyValue(MemberRespDto.of(member)));
    }

    private Mono<ServerResponse> asMember(ServerRequest request, Function<DfTeamCaller, Mono<ServerResponse>> action) {
        return asCaller(request, ctx -> ctx.isDf()
                ? DfRole.fromRoles(ctx.roles()).map(role -> DfTeamCaller.member(ctx.subject(), ctx.dfId(), role))
                        .orElse(null)
                : null, action);
    }

    private Mono<ServerResponse> asCaller(ServerRequest request, Function<AuthCtx, DfTeamCaller> toCaller,
                                          Function<DfTeamCaller, Mono<ServerResponse>> action) {
        return request.principal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(token -> AuthCtx.from((Jwt) ((JwtAuthenticationToken) token).getPrincipal()))
                .mapNotNull(toCaller)
                .flatMap(action)
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    private static DfRole role(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return DfRole.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomRuleViolation(400, "invalid-member",
                    "role must be one of OWNER, ADMIN, CONSENT_MANAGER, DEVELOPER, AUDITOR");
        }
    }

    record InviteReqDto(String email, String name, String role) {
    }

    record RoleReqDto(String role) {
    }

    record MemberRespDto(String id, String email, String name, String role) {

        static MemberRespDto of(DfMember member) {
            return new MemberRespDto(member.id(), member.email(), member.name(), member.role().name());
        }
    }
}
