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

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.ntf.app.PushDvcCmdHandler;
import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * {@code /api/ntf/push-dvcs}: the calling DP's own push devices. The DP is always the caller's JWT subject (plan
 * §7a's {@link AuthCtx}), never a client-supplied id. Tokens are accepted but never echoed back.
 */
public class PushDvcHandler {

    private final PushDvcCmdHandler pushDvcHandler;

    public PushDvcHandler(PushDvcCmdHandler pushDvcHandler) {
        this.pushDvcHandler = pushDvcHandler;
    }

    public Mono<ServerResponse> register(ServerRequest request) {
        return dpId(request)
                .zipWith(request.bodyToMono(PushDvcReqDto.class)
                        .switchIfEmpty(Mono.error(new DomRuleViolation(400, "push-token-invalid", "body required"))))
                .flatMap(t -> pushDvcHandler.register(t.getT1(), t.getT2().token(), t.getT2().platform()))
                .flatMap(dvc -> ServerResponse.status(HttpStatus.CREATED).bodyValue(PushDvcRespDto.from(dvc)));
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return dpId(request)
                .flatMap(dpId -> pushDvcHandler.list(dpId).map(PushDvcRespDto::from).collectList())
                .flatMap(dvcs -> ServerResponse.ok().bodyValue(dvcs));
    }

    public Mono<ServerResponse> remove(ServerRequest request) {
        return dpId(request)
                .flatMap(dpId -> pushDvcHandler.remove(dpId, request.pathVariable("id")))
                .then(ServerResponse.noContent().build());
    }

    private Mono<String> dpId(ServerRequest request) {
        return request.principal()
                .cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()))
                .filter(AuthCtx::isDp)
                .map(AuthCtx::dpId)
                .switchIfEmpty(Mono.error(new DomRuleViolation(403, "dp-only", "Only a Data Principal has push devices")));
    }

    record PushDvcReqDto(String token, PushPltf platform) {
    }

    record PushDvcRespDto(String id, PushPltf platform, Instant registeredAt, Instant lastSeenAt) {
        static PushDvcRespDto from(PushDvc dvc) {
            return new PushDvcRespDto(dvc.id(), dvc.platform(), dvc.registeredAt(), dvc.lastSeenAt());
        }
    }
}
