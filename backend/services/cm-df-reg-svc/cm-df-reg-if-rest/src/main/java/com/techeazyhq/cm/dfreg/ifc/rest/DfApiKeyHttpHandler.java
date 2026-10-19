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

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.DfApiKeyHandler;
import com.techeazyhq.cm.dfreg.app.DfTeamCaller;
import com.techeazyhq.cm.dfreg.app.VerifyApiRequestHandler;
import com.techeazyhq.cm.dfreg.dom.DfApiKey;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * API keys (ADR-041). {@code /api/df-reg/me/api-keys} is the signed-in member's own fiduciary's keys, managed by its
 * Owners, Administrators and Developers. {@code POST /internal/api-keys/verify} is the gateway's check of an API-key
 * request, on the private network only (cm-gw doesn't route {@code /internal}).
 */
public class DfApiKeyHttpHandler {

    private final DfApiKeyHandler keys;
    private final VerifyApiRequestHandler verifier;

    public DfApiKeyHttpHandler(DfApiKeyHandler keys, VerifyApiRequestHandler verifier) {
        this.keys = keys;
        this.verifier = verifier;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return member(request).flatMap(caller -> keys.list(caller).map(KeyDto::of).collectList())
                .flatMap(list -> ServerResponse.ok().bodyValue(list));
    }

    /** The key and its signing secret are in this response only. */
    public Mono<ServerResponse> issue(ServerRequest request) {
        return member(request)
                .zipWith(request.bodyToMono(IssueReqDto.class).defaultIfEmpty(new IssueReqDto(null, false)))
                .flatMap(t -> keys.issue(t.getT1(), t.getT2().name(), t.getT2().signingRequired()))
                .flatMap(issued -> ServerResponse.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                        .bodyValue(new IssuedDto(KeyDto.of(issued.key()), issued.apiKey(), issued.signingSecret())));
    }

    public Mono<ServerResponse> revoke(ServerRequest request) {
        String id = request.pathVariable("id");
        return member(request).flatMap(caller -> keys.revoke(caller, id))
                .flatMap(key -> ServerResponse.ok().bodyValue(KeyDto.of(key)));
    }

    public Mono<ServerResponse> signingRequired(ServerRequest request) {
        String id = request.pathVariable("id");
        return member(request)
                .zipWith(request.bodyToMono(RequiredDto.class).defaultIfEmpty(new RequiredDto(false)))
                .flatMap(t -> keys.requireSigning(t.getT1(), id, t.getT2().required()))
                .flatMap(key -> ServerResponse.ok().bodyValue(KeyDto.of(key)));
    }

    public Mono<ServerResponse> verify(ServerRequest request) {
        return request.bodyToMono(VerifyReqDto.class)
                .switchIfEmpty(Mono.error(new DomRuleViolation(400, "invalid-request", "A request to verify is needed")))
                .flatMap(b -> verifier.verify(new VerifyApiRequestHandler.Request(b.apiKey(), b.method(), b.path(),
                        b.query(), b.bodySha256(), b.timestamp(), b.nonce(), b.signature())))
                .flatMap(v -> ServerResponse.ok().cacheControl(CacheControl.noStore()).bodyValue(v));
    }

    private static Mono<DfTeamCaller> member(ServerRequest request) {
        return request.principal().cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .flatMap(ctx -> ctx.isDf()
                        ? DfRole.fromRoles(ctx.roles()).map(role -> Mono.just(DfTeamCaller.member(ctx.subject(), ctx.dfId(), role)))
                                .orElseGet(() -> Mono.error(new DomRuleViolation(403, "not-permitted",
                                        "API keys are managed by people at the fiduciary, not by its systems")))
                        : Mono.error(new DomRuleViolation(403, "not-permitted", "Only a fiduciary has API keys")));
    }

    record IssueReqDto(String name, boolean signingRequired) {
    }

    record RequiredDto(boolean required) {
    }

    record VerifyReqDto(String apiKey, String method, String path, String query, String bodySha256, String timestamp,
                        String nonce, String signature) {
    }

    record KeyDto(String id, String name, String keyPrefix, boolean signingRequired, Instant createdAt,
                  Instant lastUsedAt, Instant revokedAt) {

        static KeyDto of(DfApiKey k) {
            return new KeyDto(k.id(), k.label(), k.keyPrefix(), k.signingRequired(), k.createdAt(), k.lastUsedAt(),
                    k.revokedAt());
        }
    }

    record IssuedDto(KeyDto key, String apiKey, String signingSecret) {
    }
}
