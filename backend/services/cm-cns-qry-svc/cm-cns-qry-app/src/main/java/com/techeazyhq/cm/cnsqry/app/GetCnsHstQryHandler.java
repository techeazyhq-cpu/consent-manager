package com.techeazyhq.cm.cnsqry.app;

import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdlRepoPort;

import reactor.core.publisher.Flux;

/** Stage 5: Audit & Lineage (plan §1/§8). */
public class GetCnsHstQryHandler {

    private final CnsHstRdMdlRepoPort hstRepo;

    public GetCnsHstQryHandler(CnsHstRdMdlRepoPort hstRepo) {
        this.hstRepo = hstRepo;
    }

    public Flux<CnsHstRdMdl> handle(String cnsReqId) {
        return hstRepo.findByCnsReqId(cnsReqId);
    }
}
