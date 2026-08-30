package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/** Stage 1: Capture (plan §7c step 1). Published to {@code cm.cnscmd.evt.cns-req-created.v1}. */
public record CnsReqCreatedEvt(UUID evtId, Instant occurredAt, String cnsReqId, String dfId, DpRef dpRef, Prps prps,
                                Scp scp)
        implements DomainEvt {

    public static CnsReqCreatedEvt of(String cnsReqId, String dfId, DpRef dpRef, Prps prps, Scp scp) {
        return new CnsReqCreatedEvt(UUID.randomUUID(), Instant.now(), cnsReqId, dfId, dpRef, prps, scp);
    }
}
