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

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;

import reactor.core.publisher.Flux;

/**
 * Sweeps {@code ISSUED} artefacts past their {@code ValPrd} into {@code EXPIRED} (plan §8) —
 * closes the gap a token-only PDP check leaves: without this, the read model would keep
 * showing a lapsed artefact as still issued indefinitely.
 */
public class CnsExpirySweepJob {

    private final CnsArtRepoPort cnsArtRepo;

    public CnsExpirySweepJob(CnsArtRepoPort cnsArtRepo) {
        this.cnsArtRepo = cnsArtRepo;
    }

    // @Transactional cannot be applied here: Spring's reactive transaction support requires
    // the annotated method to itself return a reactive type (Mono/Flux) to wrap — it throws
    // "Cannot apply reactive transaction to non-reactive return type [void]" on every
    // invocation for a void @Scheduled method, meaning sweep()'s body never ran at all until
    // this was removed. Found during Phase 1 verification. Each cnsArtRepo.save() below is
    // still atomic as a single-row R2DBC statement; no cross-row invariant needs protecting.
    @Scheduled(fixedDelayString = "${cm.cnscmd.expiry-sweep.fixed-delay-ms:60000}")
    public void sweep() {
        Instant now = Instant.now();
        Flux.defer(() -> cnsArtRepo.findIssuedExpiredAsOf(now))
                .doOnNext(cnsArt -> cnsArt.expire(now))
                .concatMap(cnsArtRepo::save)
                .subscribe(v -> {
                }, err -> org.slf4j.LoggerFactory.getLogger(CnsExpirySweepJob.class).warn("Expiry sweep iteration failed", err));
    }
}
