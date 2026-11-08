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


package com.techeazyhq.cm.dfreg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.dfreg.app.DfRetentionHandler;

import reactor.core.publisher.Mono;

/**
 * Deletes what has passed its retention for closed fiduciaries (ADR-064), one at a time. A failure is logged as
 * {@code [CM-DF-PURGE-FAILED]} and the fiduciary is tried again on the next pass; every step can be repeated.
 */
public class DfRetentionJob {

    private static final Logger LOG = LoggerFactory.getLogger(DfRetentionJob.class);

    private final DfRetentionHandler retention;

    public DfRetentionJob(DfRetentionHandler retention) {
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${cm.dfreg.retention.fixed-delay-ms:3600000}", initialDelayString = "${cm.dfreg.retention.initial-delay-ms:120000}")
    public void purgeDue() {
        retention.due().concatMap(dfId -> retention.purge(dfId)
                        .doOnNext(n -> LOG.info("Retention purge of fiduciary {}: {} records deleted", dfId, n))
                        .onErrorResume(e -> {
                            LOG.warn("[CM-DF-PURGE-FAILED] Purging fiduciary {} failed; it is tried again on the next pass", dfId, e);
                            return Mono.empty();
                        }))
                .subscribe(x -> { }, e -> LOG.warn("[CM-DF-PURGE-FAILED] Looking for fiduciaries to purge failed", e));
    }
}
