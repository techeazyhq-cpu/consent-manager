package com.techeazyhq.cm.cnscmd.app;

import reactor.core.publisher.Mono;

/** CQRS Command Handler (plan §8, §12) — minimal in-process contract, no external mediator library. */
public interface CmdHandler<C, R> {

    Mono<R> handle(C cmd);
}
