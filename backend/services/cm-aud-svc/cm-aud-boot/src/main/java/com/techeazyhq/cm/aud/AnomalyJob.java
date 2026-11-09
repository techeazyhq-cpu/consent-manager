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

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.techeazyhq.cm.aud.app.AnomalyDetector;

/**
 * Looks for unusual activity in the last complete hour (ADR-112), every {@code cm.aud.anomaly.interval-ms} (ten minutes by default). The
 * same hour found twice is kept once, so a short interval costs a query and nothing more. Off with {@code cm.aud.anomaly.enabled=false}.
 * A failed run is logged and tried again at the next interval: finding nothing must never stop the ledger.
 */
@Component
@ConditionalOnProperty(name = "cm.aud.anomaly.enabled", havingValue = "true", matchIfMissing = true)
public class AnomalyJob {

    private static final Logger LOG = LoggerFactory.getLogger(AnomalyJob.class);

    private final AnomalyDetector detector;

    public AnomalyJob(AnomalyDetector detector) {
        this.detector = detector;
    }

    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${cm.aud.anomaly.interval-ms:600000}")
    public void look() {
        try {
            Integer found = detector.run().block(Duration.ofMinutes(2));
            if (found != null && found > 0) {
                LOG.warn("[CM-ANOMALY-FOUND] {} unusual hour(s) found; see the Anomalies page in CM Admin", found);
            }
        } catch (RuntimeException e) {
            LOG.warn("[CM-ANOMALY-RUN-FAILED] The look for unusual activity failed; it is tried again", e);
        }
    }
}
