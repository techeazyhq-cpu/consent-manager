package com.techeazyhq.cm.cnscmd.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Port (plan §12, Repository pattern): implemented by {@code R2dbcCnsReqRepoAdapter} in -infra. */
public interface CnsReqRepoPort {

    Mono<CnsReq> save(CnsReq cnsReq);

    Mono<CnsReq> findById(String id);

    /** Every unlinked CnsReq matching a given external contact — used by LinkDpCmdHandler (plan §7a). */
    Flux<CnsReq> findUnlinkedByExtRef(ExtDpRefTyp typ, String val);
}
