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

package com.techeazyhq.cm.aud;

import com.techeazyhq.cm.aud.app.AudAnchoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Checkpoints and anchors the ledger on a fixed delay (ADR-015), once soon after start and then every
 * {@code cm.aud.anchoring.interval-ms} (an hour by default). Off with {@code cm.aud.anchoring.enabled=false}, e.g.
 * where the service cannot reach a timestamp authority.
 */
@Component
@ConditionalOnProperty(name = "cm.aud.anchoring.enabled", havingValue = "true", matchIfMissing = true)
public class AudAnchoringJob {

    private final AudAnchoringService anchoring;

    public AudAnchoringJob(AudAnchoringService anchoring) {
        this.anchoring = anchoring;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${cm.aud.anchoring.interval-ms:3600000}")
    public void checkpointAndAnchor() {
        anchoring.checkpointAndAnchor().block(Duration.ofMinutes(5));
    }
}
