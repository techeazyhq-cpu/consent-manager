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

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.ApproveCnsCmdHandler;
import com.techeazyhq.cm.cnscmd.app.ApproveCnsReqCmd;
import com.techeazyhq.cm.cnscmd.app.CnsReqTerms;
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
    private final CnsReqTerms terms;

    public CnsReqHandler(CreateCnsReqCmdHandler createHandler, ApproveCnsCmdHandler approveHandler,
                          DenyCnsReqCmdHandler denyHandler, CnsReqTerms terms) {
        this.createHandler = createHandler;
        this.approveHandler = approveHandler;
        this.denyHandler = denyHandler;
        this.terms = terms;
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
                            body.prpsCode(), body.prpsVer(), body.ntcLang(),
                            body.dCatCodes() == null ? java.util.Set.of() : body.dCatCodes());
                    return createHandler.handle(cmd);
                })
                // the domain refusing what was asked (no data categories, sizes over the limits) is the caller's
                // mistake, not a server error (VAPT INP-07 to INP-09)
                .onErrorMap(IllegalArgumentException.class, e -> new DomRuleViolation(400, "invalid-request",
                        e.getMessage()))
                .flatMap(cnsReq -> ServerResponse.status(HttpStatus.CREATED).bodyValue(CnsReqRespDto.of(cnsReq)));
    }

    /**
     * {@code POST .../cns-reqs/{id}/approve}: the principal gives consent, optionally to some of the categories
     * ({@code dCatCodes}) and for a validity they choose ({@code validityDays}); with no body, everything asked for
     * the requested validity (ADR-026).
     */
    public Mono<ServerResponse> approve(ServerRequest request) {
        String id = request.pathVariable("id");
        return asPrincipal(request, ctx -> request.bodyToMono(GrantReqDto.class)
                .defaultIfEmpty(new GrantReqDto(null, null))
                .flatMap(body -> approveHandler.handle(new ApproveCnsReqCmd(id, ctx.dpId(),
                        body.dCatCodes() == null ? null : Set.copyOf(body.dCatCodes()), body.validityDays())))
                .flatMap(cnsArt -> ServerResponse.ok().bodyValue(CnsArtRespDto.of(cnsArt))));
    }

    public Mono<ServerResponse> deny(ServerRequest request) {
        String id = request.pathVariable("id");
        return asPrincipal(request, ctx -> denyHandler.handle(new DenyCnsReqCmd(id, ctx.dpId()))
                .flatMap(cnsReq -> ServerResponse.ok().bodyValue(CnsReqRespDto.of(cnsReq))));
    }

    /** {@code GET .../cns-reqs/{id}/terms}: what the request asks, for the principal deciding and the fiduciary. */
    public Mono<ServerResponse> terms(ServerRequest request) {
        String id = request.pathVariable("id");
        return authCtx(request)
                .flatMap(ctx -> terms.forViewer(id, ctx.dpId(), ctx.dfId()))
                .flatMap(t -> ServerResponse.ok().bodyValue(CnsReqTermsDto.of(t)));
    }

    /** What the principal chose; either may be left out. */
    record GrantReqDto(List<String> dCatCodes, Integer validityDays) {
    }

    /**
     * Only the principal a request names decides on it or withdraws it. A fiduciary's people and systems are refused
     * outright: consent given by anyone else isn't consent (DPDPA s.6, GDPR art. 7).
     */
    static Mono<ServerResponse> asPrincipal(ServerRequest request, Function<AuthCtx, Mono<ServerResponse>> action) {
        return authCtx(request)
                .flatMap(ctx -> ctx.isDp() ? action.apply(ctx)
                        : Mono.<ServerResponse>error(new DomRuleViolation(403, "principal-only",
                                "Only the person asked can give, decline or withdraw consent")))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    static Mono<AuthCtx> authCtx(ServerRequest request) {
        return request.principal()
                .cast(JwtAuthenticationToken.class)
                .map(token -> AuthCtx.from((Jwt) token.getPrincipal()));
    }
}
