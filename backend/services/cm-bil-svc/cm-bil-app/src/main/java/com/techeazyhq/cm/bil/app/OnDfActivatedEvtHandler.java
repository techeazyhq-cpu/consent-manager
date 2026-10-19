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

import org.springframework.transaction.annotation.Transactional;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.bil.dom.DfSubFactory;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Choreography (plan §7c): consumed from cm-df-reg-svc's evt-df-activated topic, auto-creates the DfSub, on the plan chosen at onboarding. Idempotent. */
public class OnDfActivatedEvtHandler {

    private static final String FREE_PLAN_NAME = "FREE";

    private final DfSubRepoPort dfSubRepo;
    private final SubPlnRepoPort subPlnRepo;
    private final AuditTrail audit;

    public OnDfActivatedEvtHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo) {
        this(dfSubRepo, subPlnRepo, AuditTrail.NONE);
    }

    public OnDfActivatedEvtHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo, AuditTrail audit) {
        this.dfSubRepo = dfSubRepo;
        this.subPlnRepo = subPlnRepo;
        this.audit = audit;
    }

    /**
     * @param subPlnName the plan chosen at onboarding, checked by cm-df-reg-svc then; null (an event from before plans
     *                   were chosen) means FREE. A plan that has since disappeared fails the event, which is
     *                   redelivered, rather than quietly subscribing the fiduciary to something else.
     */
    @Transactional
    public Mono<Void> handle(String dfId, String subPlnName) {
        String planName = subPlnName == null || subPlnName.isBlank() ? FREE_PLAN_NAME : subPlnName;
        return dfSubRepo.findByDfId(dfId)
                .switchIfEmpty(subPlnRepo.findByName(planName)
                        .switchIfEmpty(Mono.error(new DomRuleViolation(500, "sub-pln-missing",
                                "SubPln " + planName + " not found for DF " + dfId)))
                        .flatMap(plan -> dfSubRepo.save(DfSubFactory.create(dfId, plan.id()))
                                .flatMap(sub -> audit.record("subscription.started", dfId, dfId,
                                        AuditTrail.details("plan", plan.name())).thenReturn(sub))))
                .then();
    }
}
