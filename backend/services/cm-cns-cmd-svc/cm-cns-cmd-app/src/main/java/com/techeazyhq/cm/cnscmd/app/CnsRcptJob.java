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

import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.cnscmd.dom.CnsRecRepoPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Issues a receipt for every record event that has none, shortly after each change (ADR-025). Each receipt is issued
 * in its own transaction through {@link CnsRecords#issue}; one that fails is left for the next pass.
 */
public class CnsRcptJob {

    private final CnsRecRepoPort recs;
    private final CnsRecords records;

    public CnsRcptJob(CnsRecRepoPort recs, CnsRecords records) {
        this.recs = recs;
        this.records = records;
    }

    private final AtomicBoolean passRunning = new AtomicBoolean();

    /**
     * One pass at a time, and the pending list read in full before issuing: overlapping passes issued the same receipt
     * twice, and a pass streaming its query while each receipt opened a transaction took every connection in the pool
     * (the outbox relay's lesson, docs/security/vapt-and-performance.md).
     */
    @Scheduled(fixedDelayString = "${cm.cns-rcpt.issue-delay-ms:2000}")
    public void issuePending() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        recs.pendingReceipts(100)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(p -> records.issue(p.cnsReqId(), p.seq()).onErrorResume(e -> {
                    org.slf4j.LoggerFactory.getLogger(CnsRcptJob.class)
                            .warn("Receipt for {} event {} not issued yet: {}", p.cnsReqId(), p.seq(), e.toString());
                    return Mono.empty();
                }))
                .doFinally(signal -> passRunning.set(false))
                .subscribe(r -> {
                }, err -> org.slf4j.LoggerFactory.getLogger(CnsRcptJob.class).warn("Receipt pass failed", err));
    }
}
