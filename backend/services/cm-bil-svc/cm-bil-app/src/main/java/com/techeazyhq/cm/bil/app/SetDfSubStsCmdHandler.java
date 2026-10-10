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

import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/** CM_ADMIN suspend/reactivate (plan §11) — suspending immediately trips §7b's hard eligibility gate. */
public class SetDfSubStsCmdHandler {

    private final DfSubRepoPort dfSubRepo;

    public SetDfSubStsCmdHandler(DfSubRepoPort dfSubRepo) {
        this.dfSubRepo = dfSubRepo;
    }

    @Transactional
    public Mono<DfSub> handle(SetDfSubStsCmd cmd) {
        return dfSubRepo.findByDfId(cmd.dfId())
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-sub-not-found", "No DfSub for " + cmd.dfId())))
                .flatMap(dfSub -> {
                    if (cmd.active()) {
                        dfSub.reactivate();
                    } else {
                        dfSub.suspend();
                    }
                    return dfSubRepo.save(dfSub);
                });
    }
}
