package com.techeazyhq.cm.cnsqry.dom;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CnsArtRdMdlRepoPort {

    Mono<CnsArtRdMdl> upsert(CnsArtRdMdl row);

    Mono<CnsArtRdMdl> findByCnsReqId(String cnsReqId);

    Mono<CnsArtRdMdl> findByCnsArtId(String cnsArtId);

    Flux<CnsArtRdMdl> findByDpId(String dpId);

    Flux<CnsArtRdMdl> findByDfId(String dfId);

    /** Sets dp_id on every row still null-dp matching (cnsReqId/cnsArtId) — used projecting CnsDpLinkedEvt (plan §7a). */
    Mono<Void> linkDp(String recordId, String recordType, String newDpId);

    Flux<DpDashboardRow> dashboardFor(String dpId);
}
