package com.techeazyhq.cm.cnscmd.ifc.rest;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmd;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmdHandler;

import reactor.core.publisher.Mono;

/** Functional-endpoint handler (plan §10) for {@code CnsArt} operations. */
public class CnsArtHandler {

    private final RevokeCnsArtCmdHandler revokeHandler;

    public CnsArtHandler(RevokeCnsArtCmdHandler revokeHandler) {
        this.revokeHandler = revokeHandler;
    }

    public Mono<ServerResponse> revoke(ServerRequest request) {
        String id = request.pathVariable("id");
        return revokeHandler.handle(new RevokeCnsArtCmd(id))
                .flatMap(cnsArt -> ServerResponse.ok().bodyValue(CnsArtRespDto.of(cnsArt)));
    }
}
