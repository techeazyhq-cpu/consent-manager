/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

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
