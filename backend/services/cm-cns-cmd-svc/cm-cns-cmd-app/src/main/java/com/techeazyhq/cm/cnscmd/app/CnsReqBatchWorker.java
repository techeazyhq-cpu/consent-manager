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

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatchRepoPort;
import com.techeazyhq.cm.cnscmd.dom.DfUsagePort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Sends queued batches (ADR-036) a chunk at a time, oldest batch first, so a large batch makes steady progress and
 * reports it. When a batch has nothing left to send it finishes, giving back the allowance its failed rows reserved.
 * One pass runs at a time in this instance; rows are claimed in the store, so other instances take other rows.
 */
public class CnsReqBatchWorker {

    private static final Logger LOG = LoggerFactory.getLogger(CnsReqBatchWorker.class);

    static final int BATCHES_PER_PASS = 5;
    static final int ROWS_PER_CHUNK = 200;

    private final CnsReqBatchRepoPort batchRepo;
    private final CnsReqBatchSender sender;
    private final DfUsagePort usage;
    private final AuditTrail audit;
    private final AtomicBoolean running = new AtomicBoolean();

    public CnsReqBatchWorker(CnsReqBatchRepoPort batchRepo, CnsReqBatchSender sender, DfUsagePort usage) {
        this(batchRepo, sender, usage, AuditTrail.NONE);
    }

    public CnsReqBatchWorker(CnsReqBatchRepoPort batchRepo, CnsReqBatchSender sender, DfUsagePort usage,
                             AuditTrail audit) {
        this.batchRepo = batchRepo;
        this.sender = sender;
        this.usage = usage;
        this.audit = audit;
    }

    @Scheduled(fixedDelayString = "${cm.cnscmd.batch-worker.fixed-delay-ms:2000}")
    public void tick() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        pass().doFinally(signal -> running.set(false))
                .subscribe(v -> {
                }, err -> LOG.warn("Batch worker pass failed", err));
    }

    /** One chunk from each unfinished batch. */
    public Mono<Void> pass() {
        return batchRepo.findUnfinished(BATCHES_PER_PASS).concatMap(this::sendChunk).then();
    }

    Mono<CnsReqBatch> sendChunk(CnsReqBatch batch) {
        return batchRepo.claimPending(batch.id(), ROWS_PER_CHUNK)
                .concatMap(row -> sender.send(batch, row)
                        .onErrorResume(err -> {
                            LOG.warn("Batch {} row {} was not sent: {}", batch.id(), row.rowNo(), err.getMessage());
                            CnsReqBatch.Row failed = row.failed("Could not be sent: " + err.getMessage());
                            return batchRepo.saveRow(batch.id(), failed).thenReturn(failed);
                        }))
                .collectList()
                .flatMap(done -> {
                    int sent = (int) done.stream().filter(r -> r.outcome() == CnsReqBatch.Outcome.SENT).count();
                    batch.progressed(sent, done.size() - sent);
                    if (!batch.allRowsDone()) {
                        return batchRepo.save(batch);
                    }
                    int unused = batch.finish();
                    Mono<Void> giveBack = unused > 0 ? usage.release(batch.dfId(), unused)
                            .onErrorResume(err -> {
                                LOG.warn("Batch {} could not give back {} reserved requests", batch.id(), unused, err);
                                return Mono.empty();
                            }) : Mono.empty();
                    return giveBack.then(batchRepo.save(batch))
                            .flatMap(saved -> audit.record("batch.finished", saved.dfId(), saved.id(),
                                    AuditTrail.details("sent", String.valueOf(saved.sent()),
                                            "failed", String.valueOf(saved.failed()),
                                            "skipped", String.valueOf(saved.invalid() + saved.duplicate())))
                                    .thenReturn(saved));
                });
    }

    /** For tests: run passes until every batch is done. */
    Flux<Void> drain(int maxPasses) {
        return Flux.range(0, maxPasses).concatMap(i -> pass());
    }
}
