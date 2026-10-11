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

import java.time.Duration;

import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.ActivateDfCmd;
import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.GetDfActiveQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfLogoQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfQryHandler;
import com.techeazyhq.cm.dfreg.app.ListDfsQryHandler;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.UploadDfLogoCmd;
import com.techeazyhq.cm.dfreg.app.UploadDfLogoCmdHandler;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Functional-endpoint handler (plan §10's preference over {@code @RestController}). */
public class DfHandler {

    private final RegisterDfCmdHandler registerHandler;
    private final ActivateDfCmdHandler activateHandler;
    private final GetDfActiveQryHandler getActiveHandler;
    private final ListDfsQryHandler listHandler;
    private final GetDfQryHandler getDfHandler;
    private final UploadDfLogoCmdHandler uploadLogoHandler;
    private final GetDfLogoQryHandler getLogoHandler;

    public DfHandler(RegisterDfCmdHandler registerHandler, ActivateDfCmdHandler activateHandler,
                      GetDfActiveQryHandler getActiveHandler, ListDfsQryHandler listHandler, GetDfQryHandler getDfHandler,
                      UploadDfLogoCmdHandler uploadLogoHandler, GetDfLogoQryHandler getLogoHandler) {
        this.registerHandler = registerHandler;
        this.activateHandler = activateHandler;
        this.getActiveHandler = getActiveHandler;
        this.listHandler = listHandler;
        this.getDfHandler = getDfHandler;
        this.uploadLogoHandler = uploadLogoHandler;
        this.getLogoHandler = getLogoHandler;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(listHandler.handle().map(DfListItemRespDto::of), DfListItemRespDto.class);
    }

    public Mono<ServerResponse> getOne(ServerRequest request) {
        return getDfHandler.handle(request.pathVariable("id"))
                .flatMap(df -> ServerResponse.ok().bodyValue(DfNameRespDto.of(df)))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> profile(ServerRequest request) {
        return getDfHandler.handle(request.pathVariable("id"))
                .flatMap(df -> ServerResponse.ok().bodyValue(DfProfileRespDto.of(df)))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> register(ServerRequest request) {
        return request.bodyToMono(DfRegisterReqDto.class)
                .flatMap(body -> registerHandler.handle(body.toCmd()))
                .flatMap(df -> ServerResponse.status(HttpStatus.CREATED).bodyValue(DfRespDto.of(df)));
    }

    public Mono<ServerResponse> activate(ServerRequest request) {
        String id = request.pathVariable("id");
        return activateHandler.handle(new ActivateDfCmd(id))
                .flatMap(df -> ServerResponse.ok().bodyValue(DfRespDto.of(df)));
    }

    /** The request body is the image itself, with its {@code Content-Type}; anything over the codec's limit is 413. */
    public Mono<ServerResponse> uploadLogo(ServerRequest request) {
        String dfId = request.pathVariable("id");
        String contentType = request.headers().contentType()
                .map(type -> type.getType() + "/" + type.getSubtype())
                .orElse(null);
        return request.bodyToMono(byte[].class)
                .onErrorMap(DataBufferLimitException.class,
                        e -> new DomRuleViolation(413, "logo-too-large", "The logo is larger than allowed"))
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> uploadLogoHandler.handle(new UploadDfLogoCmd(dfId, contentType, bytes)))
                .then(ServerResponse.noContent().build());
    }

    /** Served with {@code nosniff}, so a browser only ever renders it as the image type it was checked to be. */
    public Mono<ServerResponse> logo(ServerRequest request) {
        return getLogoHandler.handle(request.pathVariable("id"))
                .flatMap(logo -> ServerResponse.ok()
                        .contentType(MediaType.parseMediaType(logo.contentType()))
                        .header("X-Content-Type-Options", "nosniff")
                        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                        .lastModified(logo.updatedAt())
                        .bodyValue(logo.bytes()))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> isActive(ServerRequest request) {
        String id = request.pathVariable("id");
        return getActiveHandler.handle(id)
                .flatMap(active -> ServerResponse.ok().bodyValue(new ActiveRespDto(active)));
    }

    record ActiveRespDto(boolean active) {
    }
}
