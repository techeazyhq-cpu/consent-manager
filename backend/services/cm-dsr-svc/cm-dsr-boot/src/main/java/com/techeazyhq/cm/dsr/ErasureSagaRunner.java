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

package com.techeazyhq.cm.dsr;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.techeazyhq.cm.dsr.app.DpErasureHandler;
import com.techeazyhq.cm.dsr.infra.PulsarErasureSteps;

/**
 * Runs the erasure saga (ADR-059): listens for the steps' answers once the service is ready, and every minute sends
 * again whatever has waited too long for one. One pass at a time, as the outbox relays (V-03).
 */
@Component
public class ErasureSagaRunner {

    private final DpErasureHandler erasures;
    private final PulsarErasureSteps steps;
    private final AtomicBoolean passRunning = new AtomicBoolean();

    public ErasureSagaRunner(DpErasureHandler erasures, PulsarErasureSteps steps) {
        this.erasures = erasures;
        this.steps = steps;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void listen() {
        steps.listen(erasures);
    }

    @Scheduled(fixedDelayString = "${cm.dsr.erasure-redrive.fixed-delay-ms:60000}")
    public void redrive() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        erasures.redrive().doFinally(signal -> passRunning.set(false)).subscribe();
    }
}
