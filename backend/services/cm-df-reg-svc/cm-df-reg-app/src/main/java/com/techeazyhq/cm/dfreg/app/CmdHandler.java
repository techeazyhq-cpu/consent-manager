package com.techeazyhq.cm.dfreg.app;

import reactor.core.publisher.Mono;

public interface CmdHandler<C, R> {

    Mono<R> handle(C cmd);
}
