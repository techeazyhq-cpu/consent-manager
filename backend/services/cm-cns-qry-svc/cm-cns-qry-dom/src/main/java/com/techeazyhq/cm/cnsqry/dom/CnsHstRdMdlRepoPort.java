package com.techeazyhq.cm.cnsqry.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CnsHstRdMdlRepoPort {

    Mono<Void> append(CnsHstRdMdl row);

    Flux<CnsHstRdMdl> findByCnsReqId(String cnsReqId);
}
