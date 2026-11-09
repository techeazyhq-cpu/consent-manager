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
package com.techeazyhq.cm.aud.dom;

import java.time.Instant;
import java.util.UUID;

/**
 * An hour in which a fiduciary's count of one signal was far above its own usual (ADR-112). It holds ids, a signal's name, a time and
 * numbers: nothing about any principal.
 *
 * @param status         {@code OPEN} until a platform administrator acknowledges it
 * @param acknowledgedBy the administrator's id, or null while open
 */
public record AnomalyFinding(UUID id, String dfId, String signal, Instant windowStart, long observed, double expected, double score,
                             Instant detectedAt, String status, String acknowledgedBy, Instant acknowledgedAt) {

    public static final String OPEN = "OPEN";
    public static final String ACKNOWLEDGED = "ACKNOWLEDGED";
}
