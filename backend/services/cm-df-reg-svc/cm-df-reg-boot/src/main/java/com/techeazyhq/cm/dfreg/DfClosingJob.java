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

import com.techeazyhq.cm.dfreg.app.DfClosureHandler;

import reactor.core.publisher.Mono;

/**
 * Closes the fiduciaries whose wind-down has ended (ADR-064), one at a time. A fiduciary that fails is logged as
 * {@code [CM-DF-CLOSING-FAILED]} and left LEAVING; the next pass tries it again, and every step can be repeated.
 */
public class DfClosingJob {

    private static final Logger LOG = LoggerFactory.getLogger(DfClosingJob.class);

    private final DfClosureHandler closure;

    public DfClosingJob(DfClosureHandler closure) {
        this.closure = closure;
    }

    @Scheduled(fixedDelayString = "${cm.dfreg.closing.fixed-delay-ms:60000}", initialDelayString = "${cm.dfreg.closing.initial-delay-ms:30000}")
    public void closeDue() {
        closure.due().concatMap(dfId -> closure.cleanUp(dfId)
                        .flatMap(summary -> closure.finish(dfId, summary))
                        .doOnNext(df -> LOG.info("Closed fiduciary {}", dfId))
                        .onErrorResume(e -> {
                            LOG.warn("[CM-DF-CLOSING-FAILED] Closing fiduciary {} failed; it stays LEAVING and is tried again", dfId, e);
                            return Mono.empty();
                        }))
                .subscribe(x -> { }, e -> LOG.warn("[CM-DF-CLOSING-FAILED] Looking for fiduciaries to close failed", e));
    }
}
