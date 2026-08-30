package com.techeazyhq.cm.cnsqry.app;

import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class GetCnsArtQryHandler {

    private final CnsArtRdMdlRepoPort rdMdlRepo;

    public GetCnsArtQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        this.rdMdlRepo = rdMdlRepo;
    }

    public Mono<CnsArtRdMdl> byId(String id) {
        return rdMdlRepo.findByCnsArtId(id).switchIfEmpty(rdMdlRepo.findByCnsReqId(id));
    }

    public Flux<CnsArtRdMdl> byDpId(String dpId) {
        return rdMdlRepo.findByDpId(dpId);
    }

    public Flux<CnsArtRdMdl> byDfId(String dfId) {
        return rdMdlRepo.findByDfId(dfId);
    }
}
