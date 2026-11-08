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


package com.techeazyhq.cm.ntf.ifc.rest;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.ntf.app.DpPrefCmdHandler;
import com.techeazyhq.cm.ntf.dom.DpPref;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * {@code /api/ntf/preferences}: how the calling principal hears of new consent requests (ADR-066). The principal is the
 * caller's JWT subject, never a client-supplied id, and the address the email choice applies to is the verified one in their
 * token, never one they type.
 */
public class DpPrefHttpHandler {

    private final DpPrefCmdHandler prefs;

    public DpPrefHttpHandler(DpPrefCmdHandler prefs) {
        this.prefs = prefs;
    }

    public Mono<ServerResponse> get(ServerRequest request) {
        return caller(request).flatMap(c -> prefs.get(c.dpId()).map(p -> view(p, c.email())))
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    public Mono<ServerResponse> put(ServerRequest request) {
        return caller(request).zipWith(request.bodyToMono(PrefReqDto.class)
                        .switchIfEmpty(Mono.error(new DomRuleViolation(400, "invalid-request", "The preferences are needed"))))
                .flatMap(t -> prefs.set(t.getT1().dpId(), t.getT2().push(), t.getT2().email(), t.getT1().email()).map(p -> view(p, t.getT1().email())))
                .flatMap(v -> ServerResponse.ok().bodyValue(v));
    }

    private static Mono<Caller> caller(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> {
                    Jwt jwt = (Jwt) token.getPrincipal();
                    return new Caller(AuthCtx.from(jwt), jwt.getClaimAsString("email"));
                })
                .filter(c -> c.ctx().isDp())
                .map(c -> new Caller(c.ctx(), c.email()))
                .switchIfEmpty(Mono.error(new DomRuleViolation(403, "dp-only", "Only a Data Principal has notification preferences")));
    }

    private static PrefViewDto view(DpPref p, String signedInEmail) {
        return new PrefViewDto(p.pushEnabled(), p.emailRequests(), DpPrefCmdHandler.mask(signedInEmail),
                signedInEmail != null && !signedInEmail.isBlank());
    }

    private record Caller(AuthCtx ctx, String email) {
        String dpId() {
            return ctx.dpId();
        }
    }

    record PrefReqDto(boolean push, boolean email) {
    }

    /** {@code email}: organisations may email requests to {@code emailAddress} (masked); {@code canEmail}: there is one to tie it to. */
    record PrefViewDto(boolean push, boolean email, String emailAddress, boolean canEmail) {
    }
}
