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
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.DfTeamCaller;
import com.techeazyhq.cm.dfreg.app.DfTermsHandler;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfTermsAcceptance;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * The consent manager's terms and data processing agreement (ADR-063). {@code /api/df-reg/me/terms} is the signed-in
 * member's own fiduciary: its status for everyone, accepting for its Owner. {@code /api/df-reg/dfs/{id}/terms} is platform
 * staff reading a fiduciary's acceptances as evidence.
 */
public class DfTermsHttpHandler {

    private final DfTermsHandler terms;

    public DfTermsHttpHandler(DfTermsHandler terms) {
        this.terms = terms;
    }

    public Mono<ServerResponse> status(ServerRequest request) {
        return caller(request).flatMap(terms::status)
                .map(list -> list.stream().map(s -> new StatusDto(name(s.doc().name()), s.currentVersion(), s.accepted(),
                        s.acceptance() == null ? null : AcceptanceDto.of(s.acceptance()))).toList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    public Mono<ServerResponse> accept(ServerRequest request) {
        return caller(request)
                .zipWith(request.bodyToMono(AcceptReqDto.class)
                        .switchIfEmpty(Mono.error(new DomRuleViolation(400, "invalid-request", "The document and version are needed"))))
                .flatMap(t -> terms.accept(t.getT1(), t.getT2().document(), t.getT2().version()))
                .flatMap(a -> ServerResponse.status(HttpStatus.CREATED).bodyValue(AcceptanceDto.of(a)));
    }

    public Mono<ServerResponse> evidence(ServerRequest request) {
        String dfId = request.pathVariable("id");
        return caller(request).flatMap(c -> terms.evidence(c, dfId))
                .map(list -> list.stream().map(AcceptanceDto::of).toList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    private static String name(String doc) {
        return doc.toLowerCase(Locale.ROOT);
    }

    private static Mono<DfTeamCaller> caller(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .flatMap(ctx -> {
                    if (ctx.isPlatformStaff()) {
                        return Mono.just(DfTeamCaller.staff(ctx.subject()));
                    }
                    return ctx.isDf()
                            ? DfRole.fromRoles(ctx.roles()).map(role -> Mono.just(DfTeamCaller.member(ctx.subject(), ctx.dfId(), role)))
                                    .orElseGet(() -> Mono.error(new DomRuleViolation(403, "not-permitted", "Only people at the fiduciary")))
                            : Mono.error(new DomRuleViolation(403, "not-permitted", "Only a fiduciary or platform staff"));
                });
    }

    record AcceptReqDto(String document, String version) {
    }

    record StatusDto(String document, String currentVersion, boolean accepted, AcceptanceDto acceptance) {
    }

    record AcceptanceDto(String id, String document, String version, String acceptedBy, Instant acceptedAt) {

        static AcceptanceDto of(DfTermsAcceptance a) {
            return new AcceptanceDto(a.id(), name(a.doc().name()), a.version(), a.acceptedBy(), a.acceptedAt());
        }
    }
}
