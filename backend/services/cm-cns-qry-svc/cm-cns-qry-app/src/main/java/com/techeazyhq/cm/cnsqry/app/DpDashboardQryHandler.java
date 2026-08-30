package com.techeazyhq.cm.cnsqry.app;

import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.DpDashboardRow;

import reactor.core.publisher.Flux;

/** Backs the DP tile dashboard (plan §8, §17 — the user's own request): one summary row per DF. */
public class DpDashboardQryHandler {

    private final CnsArtRdMdlRepoPort rdMdlRepo;

    public DpDashboardQryHandler(CnsArtRdMdlRepoPort rdMdlRepo) {
        this.rdMdlRepo = rdMdlRepo;
    }

    public Flux<DpDashboardRow> handle(String dpId) {
        return rdMdlRepo.dashboardFor(dpId);
    }
}
