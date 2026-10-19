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

package com.techeazyhq.cm.cnscmd.ifc.rest;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.ApproveCnsCmdHandler;
import com.techeazyhq.cm.cnscmd.app.ApproveCnsReqCmd;
import com.techeazyhq.cm.cnscmd.app.CreateCnsReqCmd;
import com.techeazyhq.cm.cnscmd.app.CreateCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.DenyCnsReqCmd;
import com.techeazyhq.cm.cnscmd.app.DenyCnsReqCmdHandler;
import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Functional-endpoint handler (WebFlux {@code HandlerFunction}, plan §10) for the DF
 * Integration API's core write surface — deliberately not a {@code @RestController}, per
 * project preference for the functional-programming style over the annotation-driven one.
 * Wired into routes by {@link CnsCmdRouterConfig}; a plain class, not itself a Spring bean.
 */
public class CnsReqHandler {

    private final CreateCnsReqCmdHandler createHandler;
    private final ApproveCnsCmdHandler approveHandler;
    private final DenyCnsReqCmdHandler denyHandler;

    public CnsReqHandler(CreateCnsReqCmdHandler createHandler, ApproveCnsCmdHandler approveHandler,
                          DenyCnsReqCmdHandler denyHandler) {
        this.createHandler = createHandler;
        this.approveHandler = approveHandler;
        this.denyHandler = denyHandler;
    }

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CnsReqCreateReqDto.class)
                .zipWith(authCtx(request))
                .flatMap(tuple -> {
                    CnsReqCreateReqDto body = tuple.getT1();
                    AuthCtx ctx = tuple.getT2();
                    if (ctx.isDf() && !ctx.mayRaiseCnsReqs()) {
                        return Mono.error(new DomRuleViolation(403, "not-permitted",
                                "Your role at this fiduciary doesn't raise consent requests"));
                    }
                    CreateCnsReqCmd cmd = new CreateCnsReqCmd(
                            ctx.dfId(), body.dpId(), body.extDpRefTyp(), body.extDpRefVal(),
                            body.prpsCode(), body.dCatCodes(), body.ntcTitle(), body.ntcText());
                    return createHandler.handle(cmd);
                })
                // the domain refusing what was asked (no data categories, sizes over the limits) is the caller's
                // mistake, not a server error (VAPT INP-07 to INP-09)
                .onErrorMap(IllegalArgumentException.class, e -> new DomRuleViolation(400, "invalid-request",
                        e.getMessage()))
                .flatMap(cnsReq -> ServerResponse.status(HttpStatus.CREATED).bodyValue(CnsReqRespDto.of(cnsReq)));
    }

    public Mono<ServerResponse> approve(ServerRequest request) {
        String id = request.pathVariable("id");
        return approveHandler.handle(new ApproveCnsReqCmd(id))
                .flatMap(cnsArt -> ServerResponse.ok().bodyValue(CnsArtRespDto.of(cnsArt)));
    }

    public Mono<ServerResponse> deny(ServerRequest request) {
        String id = request.pathVariable("id");
        return denyHandler.handle(new DenyCnsReqCmd(id))
                .flatMap(cnsReq -> ServerResponse.ok().bodyValue(CnsReqRespDto.of(cnsReq)));
    }

    static Mono<AuthCtx> authCtx(ServerRequest request) {
        return request.principal()
                .cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()));
    }
}
