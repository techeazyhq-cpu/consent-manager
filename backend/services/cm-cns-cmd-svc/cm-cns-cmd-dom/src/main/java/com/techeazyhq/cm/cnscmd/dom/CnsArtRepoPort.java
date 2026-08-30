package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Port (plan §12, Repository pattern): implemented by {@code R2dbcCnsArtRepoAdapter} in -infra. */
public interface CnsArtRepoPort {

    Mono<CnsArt> save(CnsArt cnsArt);

    Mono<CnsArt> findById(String id);

    /** Every unlinked CnsArt matching a given external contact — used by LinkDpCmdHandler (plan §7a). */
    Flux<CnsArt> findUnlinkedByExtRef(ExtDpRefTyp typ, String val);

    /** Used by CnsExpirySweepJob (plan §8): every ISSUED artefact whose ValPrd.until has passed. */
    Flux<CnsArt> findIssuedExpiredAsOf(Instant now);
}
