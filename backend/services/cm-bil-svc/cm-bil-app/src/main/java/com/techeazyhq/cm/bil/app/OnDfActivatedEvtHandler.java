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

import com.techeazyhq.cm.bil.dom.DfSubFactory;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** Choreography (plan §7c): consumed from cm-df-reg-svc's evt-df-activated topic, auto-creates a FREE DfSub. Idempotent. */
public class OnDfActivatedEvtHandler {

    private static final String FREE_PLAN_NAME = "FREE";

    private final DfSubRepoPort dfSubRepo;
    private final SubPlnRepoPort subPlnRepo;

    public OnDfActivatedEvtHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo) {
        this.dfSubRepo = dfSubRepo;
        this.subPlnRepo = subPlnRepo;
    }

    @Transactional
    public Mono<Void> handle(String dfId) {
        return dfSubRepo.findByDfId(dfId)
                .switchIfEmpty(subPlnRepo.findByName(FREE_PLAN_NAME)
                        .switchIfEmpty(Mono.error(new DomRuleViolation(500, "free-plan-missing", "FREE SubPln not seeded")))
                        .flatMap(freePlan -> dfSubRepo.save(DfSubFactory.createFree(dfId, freePlan.id()))))
                .then();
    }
}
