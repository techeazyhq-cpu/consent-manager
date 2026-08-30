package com.techeazyhq.cm.dfreg.app;

import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;

import reactor.core.publisher.Mono;

/** Backs {@code GET /internal/dfs/{id}/active}, called by cm-cns-cmd-svc's DfActivePort (plan §7c step 1). */
public class GetDfActiveQryHandler {

    private final DfRepoPort dfRepo;

    public GetDfActiveQryHandler(DfRepoPort dfRepo) {
        this.dfRepo = dfRepo;
    }

    public Mono<Boolean> handle(String dfId) {
        return dfRepo.findById(dfId).map(df -> df.sts() == DfSts.ACTIVE).defaultIfEmpty(false);
    }
}
