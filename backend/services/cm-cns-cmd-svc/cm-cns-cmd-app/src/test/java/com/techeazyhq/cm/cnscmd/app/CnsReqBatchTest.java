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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch.Input;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch.Outcome;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatchRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DfUsagePort;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** ADR-036: a batch is checked, reserved all-or-nothing, then sent row by row in the background. */
class CnsReqBatchTest {

    private final Map<String, CnsReq> requests = new LinkedHashMap<>();
    private final Map<String, CnsReqBatch> batches = new LinkedHashMap<>();
    private final Map<String, List<CnsReqBatch.Row>> rows = new LinkedHashMap<>();
    private final AtomicLong allowance = new AtomicLong(1_000);
    private String failFor;

    private final CnsReqRepoPort cnsReqRepo = new CnsReqRepoPort() {
        @Override
        public Mono<CnsReq> save(CnsReq cnsReq) {
            if (cnsReq.dpRef() instanceof DpRef.UnlnkDpRef ref && ref.val().equals(failFor)) {
                return Mono.error(new IllegalStateException("store unavailable"));
            }
            requests.put(cnsReq.id(), cnsReq);
            return Mono.just(cnsReq);
        }

        @Override
        public Mono<CnsReq> findById(String id) {
            return Mono.justOrEmpty(requests.get(id));
        }

        @Override
        public Flux<CnsReq> findUnlinkedByExtRef(ExtDpRefTyp typ, String val) {
            return Flux.empty();
        }
    };

    private final CnsReqBatchRepoPort batchRepo = new CnsReqBatchRepoPort() {
        @Override
        public Mono<CnsReqBatch> create(CnsReqBatch batch, List<CnsReqBatch.Row> batchRows) {
            batches.put(batch.id(), batch);
            rows.put(batch.id(), new ArrayList<>(batchRows));
            return Mono.just(batch);
        }

        @Override
        public Mono<CnsReqBatch> save(CnsReqBatch batch) {
            batches.put(batch.id(), batch);
            return Mono.just(batch);
        }

        @Override
        public Mono<CnsReqBatch> findById(String id) {
            return Mono.justOrEmpty(batches.get(id));
        }

        @Override
        public Flux<CnsReqBatch> findByDfId(String dfId, int limit, int offset) {
            return Flux.fromIterable(batches.values()).filter(b -> b.dfId().equals(dfId));
        }

        @Override
        public Flux<CnsReqBatch> findUnfinished(int limit) {
            return Flux.fromIterable(List.copyOf(batches.values())).filter(b -> b.sts() != CnsReqBatch.Sts.DONE).take(limit);
        }

        @Override
        public Flux<CnsReqBatch.Row> claimPending(String batchId, int limit) {
            return Flux.fromIterable(rows.get(batchId).stream()
                    .filter(r -> r.outcome() == Outcome.PENDING).limit(limit).toList());
        }

        @Override
        public Mono<Void> saveRow(String batchId, CnsReqBatch.Row row) {
            List<CnsReqBatch.Row> list = rows.get(batchId);
            list.replaceAll(r -> r.rowNo() == row.rowNo() ? row : r);
            return Mono.empty();
        }

        @Override
        public Flux<CnsReqBatch.Row> rows(String batchId, Outcome outcome, int limit, int offset) {
            return Flux.fromIterable(rows.get(batchId)).filter(r -> outcome == null || r.outcome() == outcome)
                    .sort(Comparator.comparingInt(CnsReqBatch.Row::rowNo)).skip(offset).take(limit);
        }
    };

    private final DfUsagePort usage = new DfUsagePort() {
        @Override
        public Mono<Reservation> reserve(String dfId, long count) {
            if (count > allowance.get()) {
                return Mono.just(new Reservation(false, allowance.get()));
            }
            return Mono.just(new Reservation(true, allowance.addAndGet(-count)));
        }

        @Override
        public Mono<Void> release(String dfId, long count) {
            allowance.addAndGet(count);
            return Mono.empty();
        }

        @Override
        public Mono<Long> available(String dfId) {
            return Mono.just(allowance.get());
        }
    };

    private final SubmitCnsReqBatchHandler submit =
            new SubmitCnsReqBatchHandler(batchRepo, dfId -> Mono.just(true), usage);
    private final CnsReqBatchWorker worker =
            new CnsReqBatchWorker(batchRepo, new CnsReqBatchSender(cnsReqRepo, batchRepo), usage);

    private SubmitCnsReqBatchHandler.Cmd cmd(boolean dryRun, String... contacts) {
        List<Input> inputs = new ArrayList<>();
        for (String c : contacts) {
            inputs.add(new Input(c, null, null));
        }
        return new SubmitCnsReqBatchHandler.Cmd("df-1", "u-1", "ORDER_UPDATES", Set.of("EMAIL"), "Order updates",
                "We email you about your orders.", CnsReqBatch.Source.API, null, inputs, dryRun);
    }

    @Test
    void aDryRunReportsWithoutStoringReservingOrSending() {
        SubmitCnsReqBatchHandler.Result preview =
                submit.handle(cmd(true, "a@example.com", "nope", "a@example.com")).block();

        assertFalse(preview.stored());
        assertEquals(1, preview.batch().valid());
        assertEquals(2, preview.problems().size());
        assertEquals(1_000, preview.available());
        assertTrue(batches.isEmpty());
        assertEquals(1_000, allowance.get());
    }

    @Test
    void sendsEveryValidRowAsItsOwnRequestThenFinishes() {
        SubmitCnsReqBatchHandler.Result result =
                submit.handle(cmd(false, "a@example.com", "+91 98765 43210", "bad", "b@example.com")).block();
        assertEquals(997, result.available(), "three reserved at once");
        assertEquals(CnsReqBatch.Sts.QUEUED, batches.get(result.batch().id()).sts());

        worker.drain(3).blockLast();

        CnsReqBatch done = batches.get(result.batch().id());
        assertEquals(CnsReqBatch.Sts.DONE, done.sts());
        assertEquals(3, done.sent());
        assertEquals(3, requests.size());
        assertTrue(requests.values().stream().allMatch(r -> r.ntcText().equals("We email you about your orders.")));
        assertEquals(List.of(Outcome.SENT, Outcome.SENT, Outcome.INVALID, Outcome.SENT),
                rows.get(done.id()).stream().map(CnsReqBatch.Row::outcome).toList());
        assertEquals(997, allowance.get());
    }

    @Test
    void aRowThatFailsIsReportedAndItsReservationGivenBack() {
        failFor = "b@example.com";
        SubmitCnsReqBatchHandler.Result result = submit.handle(cmd(false, "a@example.com", "b@example.com")).block();

        worker.drain(3).blockLast();

        CnsReqBatch done = batches.get(result.batch().id());
        assertEquals(1, done.sent());
        assertEquals(1, done.failed());
        assertEquals(999, allowance.get(), "the failed row's request is given back");
        assertTrue(rows.get(done.id()).get(1).problem().startsWith("Could not be sent"));
    }

    @Test
    void aBatchThatDoesNotFitIsRefusedWholeAndNothingIsSent() {
        allowance.set(2);

        DomRuleViolation refused = assertThrows(DomRuleViolation.class,
                () -> submit.handle(cmd(false, "a@example.com", "b@example.com", "c@example.com")).block());

        assertEquals(429, refused.httpStatus());
        assertTrue(refused.getMessage().contains("3 people") && refused.getMessage().contains("2 requests left"));
        assertTrue(batches.isEmpty());
        assertEquals(2, allowance.get());
    }

    @Test
    void aListWithNoValidContactIsRefused() {
        DomRuleViolation refused = assertThrows(DomRuleViolation.class, () -> submit.handle(cmd(false, "x", "y")).block());

        assertEquals("no-valid-recipients", refused.problemType());
        assertEquals(1_000, allowance.get());
    }

    @Test
    void anotherFiduciarysBatchIsNotFound() {
        SubmitCnsReqBatchHandler.Result result = submit.handle(cmd(false, "a@example.com")).block();
        CnsReqBatchQueries queries = new CnsReqBatchQueries(batchRepo);

        assertEquals(result.batch().id(), queries.get("df-1", result.batch().id()).block().id());
        assertEquals(404, assertThrows(DomRuleViolation.class,
                () -> queries.get("df-2", result.batch().id()).block()).httpStatus());
    }
}
