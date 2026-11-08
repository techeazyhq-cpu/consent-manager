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

import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.DfExportHandler;
import com.techeazyhq.cm.dfreg.app.DfTeamCaller;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** {@code GET /api/df-reg/me/export}: the signed-in Owner or Administrator takes the organisation's own data as one JSON file (ADR-064). */
public class DfExportHttpHandler {

    private final DfExportHandler exports;

    public DfExportHttpHandler(DfExportHandler exports) {
        this.exports = exports;
    }

    public Mono<ServerResponse> export(ServerRequest request) {
        return caller(request).flatMap(exports::export).flatMap(bundle -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header("Content-Disposition", "attachment; filename=\"consent-manager-export-" + LocalDate.now(ZoneOffset.UTC) + ".json\"")
                .bodyValue(bundle));
    }

    private static Mono<DfTeamCaller> caller(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .flatMap(ctx -> ctx.isDf()
                        ? DfRole.fromRoles(ctx.roles()).map(role -> Mono.just(DfTeamCaller.member(ctx.subject(), ctx.dfId(), role)))
                                .orElseGet(() -> Mono.error(new DomRuleViolation(403, "not-permitted", "Only people at the fiduciary")))
                        : Mono.error(new DomRuleViolation(403, "not-permitted", "Only people at the fiduciary")));
    }
}
