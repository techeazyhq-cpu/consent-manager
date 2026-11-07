package com.techeazyhq.cm.bil.dom;

import java.time.Instant;
import java.util.UUID;

import com.techeazyhq.cm.domkit.DomainEvt;

/**
 * A fiduciary reached {@code threshold} percent of its monthly allowance (ADR-060). Consumed by cm-df-reg-svc, which
 * holds the fiduciary's contact address and tells it, and tells the platform's staff. Ids and numbers only: the
 * address never leaves cm-df-reg-svc.
 */
public record UsgThresholdEvt(UUID evtId, Instant occurredAt, String dfId, String period, int threshold, long used,
                              long quota, String planName, Instant resetsAt) implements DomainEvt {

    public static UsgThresholdEvt of(UsgAlert alert, Instant resetsAt) {
        return new UsgThresholdEvt(UUID.randomUUID(), alert.raisedAt(), alert.dfId(), alert.period(), alert.threshold(),
                alert.used(), alert.quota(), alert.planName(), resetsAt);
    }
}
