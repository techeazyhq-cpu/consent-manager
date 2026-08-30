package com.techeazyhq.cm.dfreg.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/** Consumed by cm-bil-svc to auto-create a FREE-tier DfSub (plan §7c, §11). */
public record DfActivatedEvt(UUID evtId, Instant occurredAt, String dfId, String name) implements DomainEvt {

    public static DfActivatedEvt of(String dfId, String name) {
        return new DfActivatedEvt(UUID.randomUUID(), Instant.now(), dfId, name);
    }
}
