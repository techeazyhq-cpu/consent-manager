package com.techeazyhq.cm.cnscmd.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * Raised by {@code LinkDpCmdHandler} once per {@link CnsReq}/{@link CnsArt} record it
 * retroactively links (plan §7a). Consumed by cm-cns-qry-svc's Projector to set {@code dp_id}
 * on the matching read-model rows.
 */
public record CnsDpLinkedEvt(UUID evtId, Instant occurredAt, String recordId, String recordType, String newDpId)
        implements DomainEvt {

    public static CnsDpLinkedEvt of(String recordId, String recordType, String newDpId) {
        return new CnsDpLinkedEvt(UUID.randomUUID(), Instant.now(), recordId, recordType, newDpId);
    }
}
