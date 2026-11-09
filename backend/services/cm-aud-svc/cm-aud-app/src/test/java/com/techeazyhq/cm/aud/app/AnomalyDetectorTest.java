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
package com.techeazyhq.cm.aud.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.techeazyhq.cm.aud.dom.AnomalyFinding;
import com.techeazyhq.cm.aud.dom.AnomalyPort;
import com.techeazyhq.cm.aud.dom.AnomalyScorer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class AnomalyDetectorTest {

    // 10:30, so the last complete hour is 09:00-10:00
    private static final Instant NOW = Instant.parse("2026-11-09T10:30:00Z");
    private static final Instant HOUR = Instant.parse("2026-11-09T09:00:00Z");

    /** Holds counts and keeps what is saved, once per fiduciary, signal and hour as the database does. */
    static class Fake implements AnomalyPort {
        final List<HourCount> counts = new ArrayList<>();
        final List<AnomalyFinding> saved = new ArrayList<>();
        final Set<String> keys = new HashSet<>();

        @Override
        public Flux<HourCount> hourlyCounts(Instant from, Instant to) {
            return Flux.fromIterable(counts).filter(c -> !c.hour().isBefore(from) && c.hour().isBefore(to));
        }

        @Override
        public Mono<Boolean> save(AnomalyFinding f) {
            boolean isNew = keys.add(f.dfId() + f.signal() + f.windowStart());
            if (isNew) {
                saved.add(f);
            }
            return Mono.just(isNew);
        }

        @Override
        public Flux<AnomalyFinding> list(String status, int limit) {
            return Flux.fromIterable(saved);
        }

        @Override
        public Mono<Boolean> acknowledge(UUID id, String by, Instant at) {
            return Mono.just(false);
        }
    }

    private final List<String> alerts = new ArrayList<>();

    private AnomalyDetector detector(Fake port) {
        return new AnomalyDetector(port, Clock.fixed(NOW, ZoneOffset.UTC), 14, 48, 20, 6.0, alerts::add);
    }

    /** Three days of a steady few requests an hour for a fiduciary, ending before the hour looked at. */
    private static void steady(Fake port, String df, String type, int perHour) {
        for (int h = 1; h <= 72; h++) {
            port.counts.add(new AnomalyPort.HourCount(df, type, HOUR.minus(Duration.ofHours(h)), perHour));
        }
    }

    @Test
    void aSurgeAgainstASteadyHistoryIsFoundOnceAndTheAlertCounted() {
        Fake port = new Fake();
        steady(port, "df1", "CnsReqCreatedEvt", 4);
        port.counts.add(new AnomalyPort.HourCount("df1", "CnsReqCreatedEvt", HOUR, 400));

        assertEquals(1, detector(port).run().block());
        AnomalyFinding f = port.saved.get(0);
        assertEquals("df1", f.dfId());
        assertEquals("consent-requests", f.signal());
        assertEquals(HOUR, f.windowStart());
        assertEquals(400, f.observed());
        assertEquals(AnomalyFinding.OPEN, f.status());
        assertEquals(List.of("consent-requests"), alerts);

        // the same hour looked at again is the same finding: kept once, alerted once
        assertEquals(0, detector(port).run().block());
        assertEquals(1, port.saved.size());
        assertEquals(1, alerts.size());
    }

    @Test
    void anOrdinaryHourIsNotAFinding() {
        Fake port = new Fake();
        steady(port, "df1", "CnsReqCreatedEvt", 4);
        port.counts.add(new AnomalyPort.HourCount("df1", "CnsReqCreatedEvt", HOUR, 6));
        assertEquals(0, detector(port).run().block());
    }

    @Test
    void aBusyFiduciaryIsJudgedAgainstItsOwnUsualNotAnotherOnes() {
        Fake port = new Fake();
        steady(port, "busy", "CnsReqCreatedEvt", 500);
        steady(port, "quiet", "CnsReqCreatedEvt", 2);
        port.counts.add(new AnomalyPort.HourCount("busy", "CnsReqCreatedEvt", HOUR, 560)); // normal for it
        port.counts.add(new AnomalyPort.HourCount("quiet", "CnsReqCreatedEvt", HOUR, 60)); // thirty times its usual
        assertEquals(1, detector(port).run().block());
        assertEquals("quiet", port.saved.get(0).dfId());
    }

    @Test
    void aSmallRiseUnderTheMinimumCountIsNotAFindingHoweverLargeTheRatio() {
        Fake port = new Fake();
        steady(port, "df1", "api-key.issued", 0);
        port.counts.add(new AnomalyPort.HourCount("df1", "CnsReqCreatedEvt", HOUR.minus(Duration.ofHours(80)), 1)); // history exists
        port.counts.add(new AnomalyPort.HourCount("df1", "api-key.issued", HOUR, 12)); // 12 keys: many, but below 20
        assertEquals(0, detector(port).run().block());
    }

    @Test
    void aNewFiduciaryIsStillLearningAndIsNotJudged() {
        Fake port = new Fake();
        for (int h = 1; h <= 10; h++) { // ten hours old
            port.counts.add(new AnomalyPort.HourCount("new", "CnsReqCreatedEvt", HOUR.minus(Duration.ofHours(h)), 3));
        }
        port.counts.add(new AnomalyPort.HourCount("new", "CnsReqCreatedEvt", HOUR, 900));
        assertEquals(0, detector(port).run().block());
    }

    @Test
    void anEarlierSpikeDoesNotMakeALaterOneNormal() {
        Fake port = new Fake();
        steady(port, "df1", "CnsReqCreatedEvt", 4);
        port.counts.removeIf(c -> c.hour().equals(HOUR.minus(Duration.ofHours(30))));
        port.counts.add(new AnomalyPort.HourCount("df1", "CnsReqCreatedEvt", HOUR.minus(Duration.ofHours(30)), 300)); // yesterday's spike
        port.counts.add(new AnomalyPort.HourCount("df1", "CnsReqCreatedEvt", HOUR, 300));
        assertEquals(1, detector(port).run().block());
    }

    @Test
    void eachSignalIsCountedApartAndOtherEventsAreIgnored() {
        Fake port = new Fake();
        steady(port, "df1", "CnsReqCreatedEvt", 4);
        steady(port, "df1", "webhook.created", 0);
        port.counts.add(new AnomalyPort.HourCount("df1", "CnsReqCreatedEvt", HOUR, 5));
        port.counts.add(new AnomalyPort.HourCount("df1", "webhook.created", HOUR, 50));
        port.counts.add(new AnomalyPort.HourCount("df1", "batch.submitted", HOUR, 5000)); // not a signal
        assertEquals(1, detector(port).run().block());
        assertEquals("webhooks-created", port.saved.get(0).signal());
    }

    @Test
    void theScoreIsInSpreadsAndNeverBelowTheNoiseOfCounting() {
        // a quiet baseline: mean 0.5, so the spread is at least sqrt(0.5) and at least 1
        long[] quiet = new long[100];
        for (int i = 0; i < 100; i += 2) {
            quiet[i] = 1;
        }
        AnomalyScorer.Score s = AnomalyScorer.score(3, quiet);
        assertTrue(s.spread() >= 1.0);
        assertTrue(s.score() < 6.0, "three events after a quiet history is not an attack: " + s.score());
        assertEquals(0.0, AnomalyScorer.score(10, new long[0]).score());
    }
}
