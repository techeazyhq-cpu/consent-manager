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

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.IssueSandboxKeysHandler;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.seckit.AuthCtx;

import reactor.core.publisher.Mono;

/**
 * {@code POST /api/df-reg/sandbox-keys}, served by the sandbox only (ADR-038): a person signed in to the production DF
 * Console takes API keys for their fiduciary's sandbox twin. The caller's production token says which fiduciary and
 * what role; the body only names the twin. The secret is in this response and nowhere else the caller can read it.
 */
public class SandboxKeysHttpHandler {

    public static final String PATH = "/api/df-reg/sandbox-keys";

    private final IssueSandboxKeysHandler keys;
    private final String tokenUrl;

    /** @param tokenUrl where the keys get sandbox tokens: the sandbox fiduciary realm's token endpoint */
    public SandboxKeysHttpHandler(IssueSandboxKeysHandler keys, String tokenUrl) {
        this.keys = keys;
        this.tokenUrl = tokenUrl;
    }

    public Mono<ServerResponse> issue(ServerRequest request) {
        return request.principal()
                .cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .zipWith(request.bodyToMono(ReqDto.class).defaultIfEmpty(new ReqDto(null)))
                .flatMap(callerAndBody -> {
                    AuthCtx caller = callerAndBody.getT1();
                    return keys.handle(new IssueSandboxKeysHandler.Cmd(caller.dfId(), DfRole.fromRoles(caller.roles()),
                            callerAndBody.getT2().dfName()));
                })
                .flatMap(issued -> ServerResponse.status(issued.created() ? HttpStatus.CREATED : HttpStatus.OK)
                        .cacheControl(CacheControl.noStore())
                        .bodyValue(new RespDto(issued.dfId(), issued.clientId(), issued.clientSecret(), tokenUrl,
                                issued.created())));
    }

    public record ReqDto(String dfName) {
    }

    public record RespDto(String dfId, String clientId, String clientSecret, String tokenUrl, boolean created) {
    }
}
