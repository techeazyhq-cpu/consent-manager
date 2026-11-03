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

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.cnscmd.dom.SgaStatePort;
import com.techeazyhq.cm.cnscmd.dom.SgaStep;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Runs again the saga legs left unfinished (ADR-005): STARTED or FAILED and untouched for {@code staleAfter}. Pulsar
 * redelivers a nacked leg on its own, so this catches what that misses: a leg whose message was acknowledged and then
 * lost its work, or one that kept failing. After {@code maxAttempts} the leg is GAVE_UP and stays in
 * {@code sga_state} for an operator.
 */
public class SgaRedriveJob {

    private static final System.Logger LOG = System.getLogger(SgaRedriveJob.class.getName());
    private static final int LEGS_PER_PASS = 50;

    private final SgaStatePort sgaState;
    private final CnsLifecycleSagaOrchestrator orchestrator;
    private final Duration staleAfter;
    private final int maxAttempts;
    private final Clock clock;
    private final AtomicBoolean passRunning = new AtomicBoolean();

    public SgaRedriveJob(SgaStatePort sgaState, CnsLifecycleSagaOrchestrator orchestrator, Duration staleAfter,
                         int maxAttempts, Clock clock) {
        this.sgaState = sgaState;
        this.orchestrator = orchestrator;
        this.staleAfter = staleAfter;
        this.maxAttempts = maxAttempts;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${cm.cnscmd.saga-redrive.fixed-delay-ms:60000}")
    public void sweep() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        runOnce().doFinally(signal -> passRunning.set(false))
                .subscribe(v -> {
                }, err -> LOG.log(System.Logger.Level.WARNING, "Saga re-drive pass failed", err));
    }

    /** One pass, for the scheduler and for tests. Each leg's failure is its own; the pass goes on. */
    public Mono<Void> runOnce() {
        return Flux.defer(() -> sgaState.findUnfinishedBefore(clock.instant().minus(staleAfter), LEGS_PER_PASS))
                .collectList().flatMapMany(Flux::fromIterable)
                .concatMap(this::redriveOrGiveUp)
                .then();
    }

    private Mono<Void> redriveOrGiveUp(SgaStep step) {
        if (step.attempts() >= maxAttempts) {
            LOG.log(System.Logger.Level.WARNING, "Saga leg {0} {1} for {2} gave up after {3} attempts: {4}",
                    step.sagaId(), step.typ(), step.cnsId(), step.attempts(), step.lastError());
            return sgaState.gaveUp(step.sagaId(), clock.instant());
        }
        return orchestrator.redrive(step).onErrorResume(err -> Mono.empty());
    }
}
