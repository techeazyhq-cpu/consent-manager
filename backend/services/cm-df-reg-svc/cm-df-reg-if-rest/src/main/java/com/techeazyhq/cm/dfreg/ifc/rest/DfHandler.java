package com.techeazyhq.cm.dfreg.ifc.rest;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.dfreg.app.ActivateDfCmd;
import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.GetDfActiveQryHandler;
import com.techeazyhq.cm.dfreg.app.GetDfQryHandler;
import com.techeazyhq.cm.dfreg.app.ListDfsQryHandler;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmd;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmdHandler;

import reactor.core.publisher.Mono;

/** Functional-endpoint handler (plan §10's preference over {@code @RestController}). */
public class DfHandler {

    private final RegisterDfCmdHandler registerHandler;
    private final ActivateDfCmdHandler activateHandler;
    private final GetDfActiveQryHandler getActiveHandler;
    private final ListDfsQryHandler listHandler;
    private final GetDfQryHandler getDfHandler;

    public DfHandler(RegisterDfCmdHandler registerHandler, ActivateDfCmdHandler activateHandler,
                      GetDfActiveQryHandler getActiveHandler, ListDfsQryHandler listHandler, GetDfQryHandler getDfHandler) {
        this.registerHandler = registerHandler;
        this.activateHandler = activateHandler;
        this.getActiveHandler = getActiveHandler;
        this.listHandler = listHandler;
        this.getDfHandler = getDfHandler;
    }

    public Mono<ServerResponse> list(ServerRequest request) {
        return ServerResponse.ok().body(listHandler.handle().map(DfListItemRespDto::of), DfListItemRespDto.class);
    }

    public Mono<ServerResponse> getOne(ServerRequest request) {
        return getDfHandler.handle(request.pathVariable("id"))
                .flatMap(df -> ServerResponse.ok().bodyValue(DfNameRespDto.of(df)))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> register(ServerRequest request) {
        return request.bodyToMono(DfRegisterReqDto.class)
                .flatMap(body -> registerHandler.handle(new RegisterDfCmd(body.name(), body.contactEmail())))
                .flatMap(df -> ServerResponse.status(HttpStatus.CREATED).bodyValue(DfRespDto.of(df)));
    }

    public Mono<ServerResponse> activate(ServerRequest request) {
        String id = request.pathVariable("id");
        return activateHandler.handle(new ActivateDfCmd(id))
                .flatMap(df -> ServerResponse.ok().bodyValue(DfRespDto.of(df)));
    }

    public Mono<ServerResponse> isActive(ServerRequest request) {
        String id = request.pathVariable("id");
        return getActiveHandler.handle(id)
                .flatMap(active -> ServerResponse.ok().bodyValue(new ActiveRespDto(active)));
    }

    record ActiveRespDto(boolean active) {
    }
}
