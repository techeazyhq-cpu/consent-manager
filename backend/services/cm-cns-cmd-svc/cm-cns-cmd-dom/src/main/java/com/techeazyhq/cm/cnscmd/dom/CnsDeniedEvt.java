package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/** Stage 2 decided: deny (plan §7c step 3). */
public record CnsDeniedEvt(UUID evtId, Instant occurredAt, String cnsReqId, String dfId, DpRef dpRef)
        implements DomainEvt {

    public static CnsDeniedEvt of(String cnsReqId, String dfId, DpRef dpRef) {
        return new CnsDeniedEvt(UUID.randomUUID(), Instant.now(), cnsReqId, dfId, dpRef);
    }
}
