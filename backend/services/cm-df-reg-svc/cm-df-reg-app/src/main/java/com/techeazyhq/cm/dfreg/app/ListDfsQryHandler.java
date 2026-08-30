package com.techeazyhq.cm.dfreg.app;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;

import reactor.core.publisher.Flux;

/** Backs {@code GET /api/df-reg/dfs} — CM Admin's DF management list (mf-cm-admin). */
public class ListDfsQryHandler {

    private final DfRepoPort dfRepo;

    public ListDfsQryHandler(DfRepoPort dfRepo) {
        this.dfRepo = dfRepo;
    }

    public Flux<Df> handle() {
        return dfRepo.findAll();
    }
}
