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

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.DfSubSts;
import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.bil.dom.UsgAlert;
import com.techeazyhq.cm.bil.dom.UsgPort;

import reactor.core.publisher.Mono;

/**
 * Compares every active fiduciary's usage this month with its plan, and raises the alert for the highest threshold
 * reached (ADR-060). Usage is a Redis counter the gateway bumps on every call, so there is no one place in billing where
 * it crosses a line: a sweep reads it instead, every minute. A suspended subscription is skipped, since its requests
 * are refused whatever it has used.
 */
public class UsgAlertSweep {

    private static final Logger LOG = LoggerFactory.getLogger(UsgAlertSweep.class);
    private static final int CONCURRENCY = 4;

    private final DfSubRepoPort subs;
    private final SubPlnRepoPort plans;
    private final UsgPort usage;
    private final UsgAlertHandler alerts;
    private final Clock clock;

    public UsgAlertSweep(DfSubRepoPort subs, SubPlnRepoPort plans, UsgPort usage, UsgAlertHandler alerts, Clock clock) {
        this.subs = subs;
        this.plans = plans;
        this.usage = usage;
        this.alerts = alerts;
        this.clock = clock;
    }

    /** How many alerts this pass raised. */
    public Mono<Long> run() {
        YearMonth month = YearMonth.now(clock.withZone(ZoneOffset.UTC));
        Instant resetsAt = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return plans.findAll().collectMap(SubPln::id)
                .flatMap(byId -> subs.findAll()
                        .filter(sub -> sub.sts() == DfSubSts.ACTIVE && byId.containsKey(sub.subPlnId()))
                        .flatMap(sub -> check(sub, byId.get(sub.subPlnId()), month, resetsAt)
                                .onErrorResume(e -> {
                                    LOG.warn("Usage alert check failed for {}", sub.id(), e);
                                    return Mono.just(false);
                                }), CONCURRENCY)
                        .filter(Boolean::booleanValue)
                        .count());
    }

    private Mono<Boolean> check(DfSub sub, SubPln plan, YearMonth month, Instant resetsAt) {
        return usage.currentPeriodUsage(sub.id()).flatMap(used -> UsgAlert.highestReached(used, plan.reqPerMonthQta())
                .stream().boxed().findFirst()
                .map(threshold -> alerts.raise(new UsgAlert(sub.id(), month.toString(), threshold, used,
                        plan.reqPerMonthQta(), plan.name(), clock.instant()), resetsAt))
                .orElseGet(() -> Mono.just(false)));
    }
}
