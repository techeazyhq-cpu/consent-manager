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

package com.techeazyhq.cm.stf.ifc.rest;

import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.seckit.AuthCtx;
import com.techeazyhq.cm.stf.app.CreateOperatorCmdHandler;
import com.techeazyhq.cm.stf.app.ListStfQryHandler;
import com.techeazyhq.cm.stf.app.MyAcctHandler;
import com.techeazyhq.cm.stf.app.SetStfEnabledCmdHandler;
import com.techeazyhq.cm.stf.app.StfCaller;
import com.techeazyhq.cm.stf.dom.StfSession;

import reactor.core.publisher.Mono;

/**
 * {@code /api/stf/**}. Every route needs a member of platform staff, checked here from the caller's token as well as
 * at the gateway; which of them may do what (only administrators manage staff) is the application layer's rule.
 */
public class StfHandler {

    private final CreateOperatorCmdHandler createOperatorHandler;
    private final SetStfEnabledCmdHandler setEnabledHandler;
    private final ListStfQryHandler listHandler;
    private final MyAcctHandler myAcctHandler;

    public StfHandler(CreateOperatorCmdHandler createOperatorHandler, SetStfEnabledCmdHandler setEnabledHandler,
                      ListStfQryHandler listHandler, MyAcctHandler myAcctHandler) {
        this.createOperatorHandler = createOperatorHandler;
        this.setEnabledHandler = setEnabledHandler;
        this.listHandler = listHandler;
        this.myAcctHandler = myAcctHandler;
    }

    public Mono<ServerResponse> me(ServerRequest request) {
        return asStaff(request, caller -> myAcctHandler.profile(caller)
                .flatMap(acct -> ServerResponse.ok().bodyValue(acct)));
    }

    public Mono<ServerResponse> mySessions(ServerRequest request) {
        return asStaff(request, caller -> ServerResponse.ok().body(myAcctHandler.sessions(caller), StfSession.class));
    }

    public Mono<ServerResponse> signOutEverywhere(ServerRequest request) {
        return asStaff(request, caller -> myAcctHandler.signOutEverywhere(caller)
                .then(ServerResponse.noContent().build()));
    }

    public Mono<ServerResponse> listStaff(ServerRequest request) {
        return asStaff(request, caller -> listHandler.handle(caller).collectList()
                .flatMap(staff -> ServerResponse.ok().bodyValue(staff)));
    }

    public Mono<ServerResponse> createOperator(ServerRequest request) {
        return asStaff(request, caller -> request.bodyToMono(NewOperatorReqDto.class)
                .defaultIfEmpty(new NewOperatorReqDto(null, null))
                .flatMap(body -> createOperatorHandler.handle(
                        new CreateOperatorCmdHandler.Cmd(caller, body.email(), body.name())))
                .flatMap(acct -> ServerResponse.status(HttpStatus.CREATED).bodyValue(acct)));
    }

    public Mono<ServerResponse> setEnabled(ServerRequest request) {
        String stfId = request.pathVariable("id");
        return asStaff(request, caller -> request.bodyToMono(EnabledReqDto.class)
                .flatMap(body -> setEnabledHandler.handle(new SetStfEnabledCmdHandler.Cmd(caller, stfId, body.enabled())))
                .flatMap(acct -> ServerResponse.ok().bodyValue(acct))
                .switchIfEmpty(ServerResponse.badRequest().build()));
    }

    private Mono<ServerResponse> asStaff(ServerRequest request, Function<StfCaller, Mono<ServerResponse>> action) {
        return request.principal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(token -> AuthCtx.from((Jwt) ((JwtAuthenticationToken) token).getPrincipal()))
                .filter(AuthCtx::isPlatformStaff)
                .flatMap(ctx -> action.apply(new StfCaller(ctx.subject(), ctx.isCmAdmin())))
                .switchIfEmpty(Mono.defer(() -> ServerResponse.status(HttpStatus.FORBIDDEN).build()));
    }

    record NewOperatorReqDto(String email, String name) {
    }

    record EnabledReqDto(boolean enabled) {
    }
}
