package com.techeazyhq.cm.cnsqry.app;

import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;

import reactor.core.publisher.Mono;

/** Backs {@code GET /internal/cns-arts/{id}/status} — the PDP's live revocation check (plan §9). */
public class InternalStatusQryHandler {

    private final CnsArtRdMdlRepoPort rdMdlRepo;

    public InternalStatusQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        this.rdMdlRepo = rdMdlRepo;
    }

    public Mono<CnsLcySts> handle(String cnsArtId) {
        return rdMdlRepo.findByCnsArtId(cnsArtId).map(row -> row.lcySts()).defaultIfEmpty(CnsLcySts.EXPIRED);
    }
}
