package com.techeazyhq.cm.bil.app;

import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.bil.dom.UsgPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Backs {@code GET /api/bil/dfs/{id}/sub-status} — mf-cm-admin's DF management table (plan §11). */
public class GetDfSubStatusQryHandler {

    private final DfSubRepoPort dfSubRepo;
    private final SubPlnRepoPort subPlnRepo;
    private final UsgPort usgPort;

    public GetDfSubStatusQryHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo, UsgPort usgPort) {
        this.dfSubRepo = dfSubRepo;
        this.subPlnRepo = subPlnRepo;
        this.usgPort = usgPort;
    }

    public Mono<DfSubStatusResult> handle(String dfId) {
        return dfSubRepo.findByDfId(dfId)
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-sub-not-found", "No DfSub for " + dfId)))
                .flatMap(dfSub -> subPlnRepo.findById(dfSub.subPlnId())
                        .switchIfEmpty(Mono.error(new DomRuleViolation(500, "sub-pln-missing", "SubPln " + dfSub.subPlnId() + " not found")))
                        .zipWith(usgPort.currentPeriodUsage(dfId))
                        .map(tuple -> new DfSubStatusResult(
                                dfSub.subPlnId(), tuple.getT1().name(), dfSub.sts(), tuple.getT2(), tuple.getT1().reqPerMonthQta())));
    }
}
