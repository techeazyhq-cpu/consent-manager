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

package com.techeazyhq.cm.dsr.app;

import java.time.Duration;

/**
 * How long the answerer has, fixed when a request is received (ADR-021). Configuration until regime profiles carry
 * them (ADR-020): DPDPA Rules give a grievance at most 90 days, and the platform holds relayed requests to the same.
 */
public record DsrDeadlines(Duration relay, Duration grievance) {

    public static final DsrDeadlines DPDPA = new DsrDeadlines(Duration.ofDays(90), Duration.ofDays(90));

    public DsrDeadlines {
        if (relay == null || relay.isNegative() || relay.isZero() || grievance == null || grievance.isNegative()
                || grievance.isZero()) {
            throw new IllegalArgumentException("Deadlines must be positive");
        }
    }
}
