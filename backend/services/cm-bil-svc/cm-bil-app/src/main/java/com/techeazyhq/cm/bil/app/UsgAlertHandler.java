package com.techeazyhq.cm.bil.app;

import java.time.Instant;

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.bil.dom.UsgAlert;
import com.techeazyhq.cm.bil.dom.UsgAlertEvtPort;
import com.techeazyhq.cm.bil.dom.UsgAlertRepoPort;
import com.techeazyhq.cm.bil.dom.UsgThresholdEvt;

import reactor.core.publisher.Mono;

/**
 * Raises one usage alert (ADR-060): records it, and in the same transaction writes the event that tells the fiduciary
 * and the platform, and the audit entry. If the alert already exists nothing else is written, so a sweep repeated
 * every minute, or run by two replicas, raises each alert once.
 */
public class UsgAlertHandler {

    private final UsgAlertRepoPort alerts;
    private final UsgAlertEvtPort events;
    private final AuditTrail audit;

    public UsgAlertHandler(UsgAlertRepoPort alerts, UsgAlertEvtPort events, AuditTrail audit) {
        this.alerts = alerts;
        this.events = events;
        this.audit = audit;
    }

    /** True when the alert was new. */
    @Transactional
    public Mono<Boolean> raise(UsgAlert alert, Instant resetsAt) {
        return alerts.addIfNew(alert).flatMap(isNew -> !isNew ? Mono.just(false)
                : events.thresholdReached(UsgThresholdEvt.of(alert, resetsAt))
                        .then(audit.record("usage.alert", alert.dfId(), alert.period() + "/" + alert.threshold(),
                                AuditTrail.details("threshold", alert.threshold() + "%", "used", String.valueOf(alert.used()),
                                        "quota", String.valueOf(alert.quota()), "plan", alert.planName())))
                        .thenReturn(true));
    }
}
