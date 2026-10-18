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

import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.DfSubSts;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;
import com.techeazyhq.cm.bil.dom.UsgPort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Reserves this month's allowance for a batch of consent requests (ADR-036) before any is sent: all of it or none. The
 * reservation is one atomic add, undone if it overshoots the plan, so two batches arriving together can't both slip
 * under the limit. What a batch reserved but didn't send is released when it finishes.
 */
public class UsgReservationHandler {

    /** A batch's most; anything larger is a mistake, not a batch. */
    static final long MAX_COUNT = 100_000;

    private final DfSubRepoPort dfSubRepo;
    private final SubPlnRepoPort subPlnRepo;
    private final UsgPort usgPort;

    public UsgReservationHandler(DfSubRepoPort dfSubRepo, SubPlnRepoPort subPlnRepo, UsgPort usgPort) {
        this.dfSubRepo = dfSubRepo;
        this.subPlnRepo = subPlnRepo;
        this.usgPort = usgPort;
    }

    /** {@code reserved} says whether the count fits; {@code available} is what is left this month either way. */
    public record Reservation(boolean reserved, long available) {
    }

    public Mono<Reservation> reserve(String dfId, long count) {
        if (count < 1 || count > MAX_COUNT) {
            return Mono.error(new DomRuleViolation(400, "invalid-count", "count must be from 1 to " + MAX_COUNT));
        }
        return dfSubRepo.findByDfId(dfId)
                .switchIfEmpty(Mono.error(new DomRuleViolation(404, "df-sub-not-found", "No DfSub for " + dfId)))
                .flatMap(sub -> sub.sts() != DfSubSts.ACTIVE
                        ? Mono.error(new DomRuleViolation(402, "sub-inactive", "The subscription is not active"))
                        : subPlnRepo.findById(sub.subPlnId())
                                .switchIfEmpty(Mono.error(new DomRuleViolation(500, "sub-pln-missing",
                                        "SubPln " + sub.subPlnId() + " not found"))))
                .flatMap(plan -> usgPort.add(dfId, count).flatMap(total -> {
                    long limit = plan.reqPerMonthQta();
                    if (total <= limit) {
                        return Mono.just(new Reservation(true, limit - total));
                    }
                    long before = total - count;
                    return usgPort.add(dfId, -count).thenReturn(new Reservation(false, Math.max(0, limit - before)));
                }));
    }

    /** Gives back what a batch reserved but didn't send. */
    public Mono<Void> release(String dfId, long count) {
        if (count < 1 || count > MAX_COUNT) {
            return Mono.error(new DomRuleViolation(400, "invalid-count", "count must be from 1 to " + MAX_COUNT));
        }
        return usgPort.add(dfId, -count).then();
    }
}
