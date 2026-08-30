package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/** Stage 2 decided: approve (plan §7c step 3). Carries the signed {@code CnsTkn} and the artefact's ValPrd. */
public record CnsGrantedEvt(
        UUID evtId, Instant occurredAt, String cnsReqId, String cnsArtId, String dfId, DpRef dpRef, String cnsTkn,
        ValPrd valPrd)
        implements DomainEvt {

    public static CnsGrantedEvt of(String cnsReqId, String cnsArtId, String dfId, DpRef dpRef, String cnsTkn, ValPrd valPrd) {
        return new CnsGrantedEvt(UUID.randomUUID(), Instant.now(), cnsReqId, cnsArtId, dfId, dpRef, cnsTkn, valPrd);
    }
}
