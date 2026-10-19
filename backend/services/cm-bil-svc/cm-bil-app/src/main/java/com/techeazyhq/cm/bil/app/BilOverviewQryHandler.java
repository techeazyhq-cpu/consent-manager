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

import java.util.Comparator;
import java.util.List;

import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.bil.dom.UsgPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Every fiduciary's subscription with this month's usage, for CM Admin's billing overview (ADR-039): the closest to
 * their allowance first, so the tenants about to be refused are at the top.
 */
public class BilOverviewQryHandler {

    private final DfSubRepoPort subs;
    private final SubPlnRepoPort plans;
    private final UsgPort usage;

    public BilOverviewQryHandler(DfSubRepoPort subs, SubPlnRepoPort plans, UsgPort usage) {
        this.subs = subs;
        this.plans = plans;
        this.usage = usage;
    }

    public record Row(String dfId, DfSubStatusResult sub) {

        double share() {
            return sub.reqPerMonthQta() == 0 ? 0 : (double) sub.curPeriodUsg() / sub.reqPerMonthQta();
        }
    }

    public Mono<List<Row>> handle() {
        return plans.findAll().collectMap(SubPln::id)
                .flatMap(byId -> subs.findAll()
                        .filter(sub -> byId.containsKey(sub.subPlnId()))
                        .flatMap(sub -> usage.currentPeriodUsage(sub.id())
                                .map(used -> new Row(sub.id(), GetDfSubStatusQryHandler.of(sub, byId.get(sub.subPlnId()), used))), 8)
                        .collectSortedList(Comparator.comparingDouble(Row::share).reversed()
                                .thenComparing(Row::dfId)));
    }

    /** The plans with what they allow, in order of size. */
    public Flux<SubPln> plans() {
        return plans.findAll().sort(Comparator.comparingLong(SubPln::reqPerMonthQta));
    }
}
