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

package com.techeazyhq.cm.cnscmd.app;

import java.util.List;
import java.util.Set;

import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatchRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqTemplate;
import com.techeazyhq.cm.cnscmd.dom.DfActivePort;
import com.techeazyhq.cm.cnscmd.dom.DfUsagePort;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Mono;

/**
 * Takes a batch of consent requests (ADR-036): one template, a list of people. The list is checked first; a dry run
 * stops there and reports what would happen. Otherwise the whole batch's allowance is reserved (all of it or nothing),
 * the batch is stored, and {@link CnsReqBatchWorker} sends it in the background.
 */
public class SubmitCnsReqBatchHandler {

    /** How many problem rows a response lists; the batch's rows hold all of them. */
    static final int PROBLEMS_SHOWN = 100;

    private final CnsReqBatchRepoPort batchRepo;
    private final DfActivePort dfActive;
    private final DfUsagePort usage;

    public SubmitCnsReqBatchHandler(CnsReqBatchRepoPort batchRepo, DfActivePort dfActive, DfUsagePort usage) {
        this.batchRepo = batchRepo;
        this.dfActive = dfActive;
        this.usage = usage;
    }

    public record Cmd(String dfId, String createdBy, String prpsCode, Set<String> dCatCodes, String ntcTitle,
                      String ntcText, CnsReqBatch.Source source, String fileName, List<CnsReqBatch.Input> inputs,
                      boolean dryRun) {
    }

    /** {@code stored}: false for a dry run; {@code available}: requests left this month after this batch (if stored). */
    public record Result(CnsReqBatch batch, List<CnsReqBatch.Row> problems, long available, boolean stored) {
    }

    public Mono<Result> handle(Cmd cmd) {
        CnsReqBatch.Plan plan;
        try {
            CnsReqTemplate template = CnsReqTemplate.of(cmd.prpsCode(), cmd.dCatCodes(), cmd.ntcTitle(), cmd.ntcText());
            plan = CnsReqBatch.plan(cmd.dfId(), template, cmd.source(), cmd.fileName(), cmd.createdBy(), cmd.inputs());
        } catch (IllegalArgumentException e) {
            return Mono.error(new DomRuleViolation(400, "invalid-batch", e.getMessage()));
        }
        CnsReqBatch batch = plan.batch();
        List<CnsReqBatch.Row> problems = plan.problems().stream().limit(PROBLEMS_SHOWN).toList();

        return dfActive.isActive(cmd.dfId())
                .flatMap(active -> active ? Mono.<Void>empty()
                        : Mono.error(new DomRuleViolation(404, "df-not-active", "DF " + cmd.dfId() + " is not active")))
                .then(Mono.defer(() -> {
                    if (cmd.dryRun()) {
                        return usage.available(cmd.dfId()).map(left -> new Result(batch, problems, left, false));
                    }
                    if (batch.valid() == 0) {
                        return Mono.error(new DomRuleViolation(400, "no-valid-recipients",
                                "None of the " + batch.total() + " rows is an email address or mobile number "
                                        + "that can be sent to"));
                    }
                    return usage.reserve(cmd.dfId(), batch.valid()).flatMap(reservation -> reservation.reserved()
                            ? batchRepo.create(batch, plan.rows())
                                    // never keep a reservation for a batch that wasn't stored
                                    .onErrorResume(e -> usage.release(cmd.dfId(), batch.valid()).then(Mono.error(e)))
                                    .map(stored -> new Result(stored, problems, reservation.available(), true))
                            : Mono.error(new DomRuleViolation(429, "quota-exceeded", "This batch has " + batch.valid()
                                    + " people to send to, but your plan has " + reservation.available()
                                    + " requests left this month. Nothing was sent.")));
                }));
    }
}
