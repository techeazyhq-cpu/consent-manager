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

import java.util.Arrays;

/**
 * How unusual this hour's count is, against the same fiduciary's own recent hours (ADR-112). Plain statistics, no model to train and
 * nothing personal in it: a count per hour.
 *
 * <p>The baseline is the hours before, with the busiest 5% set aside so that one earlier spike does not teach the scorer that spikes are
 * normal. The score is the distance from the baseline's mean in units of its spread, where the spread is never less than the square root of
 * the mean (the natural noise of counted events) nor less than 1, so a quiet signal that goes from 0 to 2 does not look like an attack.
 */
public final class AnomalyScorer {

    private AnomalyScorer() {
    }

    /** What was expected (the baseline's mean) and how far the observed count is from it, in spreads. */
    public record Score(double expected, double spread, double score) {
    }

    /** @param baseline the counts of the hours before, zeros included; empty gives a score of 0 */
    public static Score score(long observed, long[] baseline) {
        if (baseline.length == 0) {
            return new Score(0, 1, 0);
        }
        long[] sorted = baseline.clone();
        Arrays.sort(sorted);
        int keep = sorted.length - (int) Math.floor(sorted.length * 0.05);
        double sum = 0;
        for (int i = 0; i < keep; i++) {
            sum += sorted[i];
        }
        double mean = sum / keep;
        double variance = 0;
        for (int i = 0; i < keep; i++) {
            variance += (sorted[i] - mean) * (sorted[i] - mean);
        }
        double sd = Math.sqrt(variance / keep);
        double spread = Math.max(Math.max(sd, Math.sqrt(mean)), 1.0);
        return new Score(mean, spread, (observed - mean) / spread);
    }
}
