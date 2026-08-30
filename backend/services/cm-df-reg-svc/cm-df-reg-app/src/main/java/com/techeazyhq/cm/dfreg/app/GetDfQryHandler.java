package com.techeazyhq.cm.dfreg.app;

import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;

import reactor.core.publisher.Mono;

/** Backs {@code GET /api/df-reg/dfs/{id}} — a non-sensitive name lookup any authenticated
 * role can call (e.g. mf-dp-portal resolving a DF's display name for its tile dashboard). */
public class GetDfQryHandler {

    private final DfRepoPort dfRepo;

    public GetDfQryHandler(DfRepoPort dfRepo) {
        this.dfRepo = dfRepo;
    }

    public Mono<Df> handle(String id) {
        return dfRepo.findById(id);
    }
}
