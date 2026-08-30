package com.techeazyhq.cm.bil.app;

import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;

import reactor.core.publisher.Flux;

/** Backs {@code GET /api/bil/sub-plns} — lets mf-cm-admin populate the plan-assignment dropdown. */
public class ListSubPlnsQryHandler {

    private final SubPlnRepoPort subPlnRepo;

    public ListSubPlnsQryHandler(SubPlnRepoPort subPlnRepo) {
        this.subPlnRepo = subPlnRepo;
    }

    public Flux<SubPln> handle() {
        return subPlnRepo.findAll();
    }
}
