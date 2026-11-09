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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.techeazyhq.cm.aud.dom.AnomalyFinding;
import com.techeazyhq.cm.aud.dom.AnomalyPort;
import com.techeazyhq.cm.aud.dom.AnomalyPort.HourCount;
import com.techeazyhq.cm.aud.dom.AnomalyScorer;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Looks for a fiduciary whose activity in the last complete hour is far above its own usual (ADR-112), from what the audit ledger already
 * holds: how many events of each kind a fiduciary had in each hour. It reads no payload, so no personal data comes into it.
 *
 * <p>A fiduciary is judged only once it has {@code minHistoryHours} of history, so a new one launching is not an alarm, and a signal only
 * when it reaches {@code minCount} in the hour and {@code threshold} spreads above the usual. Running it again over the same hour finds the
 * same findings, which are kept once.
 */
public class AnomalyDetector {

    /** A kind of activity, named for the people who read the finding, and the ledger event types it counts. */
    public record Signal(String name, Set<String> evtTypes) {
    }

    public static final List<Signal> SIGNALS = List.of(
            new Signal("consent-requests", Set.of("CnsReqCreatedEvt")),
            new Signal("consent-withdrawals", Set.of("CnsRevokedEvt")),
            new Signal("consent-denials", Set.of("CnsDeniedEvt")),
            new Signal("api-keys-issued", Set.of("api-key.issued")),
            new Signal("webhooks-created", Set.of("webhook.created")),
            new Signal("principal-requests", Set.of("dp-request.raised", "dp-erasure.requested", "dp-grievance.raised")));

    private final AnomalyPort port;
    private final Clock clock;
    private final int baselineDays;
    private final int minHistoryHours;
    private final long minCount;
    private final double threshold;
    private final Consumer<String> onFinding;

    public AnomalyDetector(AnomalyPort port, Clock clock, int baselineDays, int minHistoryHours, long minCount, double threshold,
                           Consumer<String> onFinding) {
        this.port = port;
        this.clock = clock;
        this.baselineDays = Math.max(2, baselineDays);
        this.minHistoryHours = Math.max(1, minHistoryHours);
        this.minCount = Math.max(1, minCount);
        this.threshold = threshold;
        this.onFinding = onFinding;
    }

    /** Looks at the last complete hour; how many findings were new. */
    public Mono<Integer> run() {
        Instant now = clock.instant();
        Instant hour = now.truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.HOURS); // the last complete hour
        Instant from = hour.minus(Duration.ofDays(baselineDays));
        return port.hourlyCounts(from, hour.plus(1, ChronoUnit.HOURS)).collectList()
                .flatMapMany(counts -> Flux.fromIterable(findings(counts, hour, from, now)))
                .concatMap(f -> port.save(f).doOnNext(isNew -> {
                    if (isNew) {
                        onFinding.accept(f.signal());
                    }
                }))
                .filter(Boolean::booleanValue)
                .count()
                .map(Long::intValue);
    }

    List<AnomalyFinding> findings(List<HourCount> counts, Instant hour, Instant from, Instant now) {
        // per fiduciary: when it was first seen at all in the window, and per signal its count by hour
        Map<String, Instant> firstSeen = new HashMap<>();
        Map<String, Map<String, Map<Instant, Long>>> bySignal = new HashMap<>();
        for (HourCount c : counts) {
            firstSeen.merge(c.dfId(), c.hour(), (a, b) -> a.isBefore(b) ? a : b);
            for (Signal s : SIGNALS) {
                if (s.evtTypes().contains(c.evtType())) {
                    bySignal.computeIfAbsent(c.dfId(), k -> new HashMap<>()).computeIfAbsent(s.name(), k -> new HashMap<>())
                            .merge(c.hour(), c.count(), Long::sum);
                }
            }
        }
        List<AnomalyFinding> out = new ArrayList<>();
        bySignal.forEach((dfId, signals) -> {
            Instant start = firstSeen.get(dfId).isAfter(from) ? firstSeen.get(dfId) : from;
            long hours = Duration.between(start, hour).toHours();
            if (hours < minHistoryHours) {
                return; // still learning what usual is for this fiduciary
            }
            signals.forEach((signal, byHour) -> {
                long observed = byHour.getOrDefault(hour, 0L);
                if (observed < minCount) {
                    return;
                }
                long[] baseline = new long[(int) hours];
                for (int i = 0; i < hours; i++) {
                    baseline[i] = byHour.getOrDefault(start.plus(i, ChronoUnit.HOURS), 0L);
                }
                AnomalyScorer.Score s = AnomalyScorer.score(observed, baseline);
                if (s.score() >= threshold) {
                    out.add(new AnomalyFinding(UUID.randomUUID(), dfId, signal, hour, observed, s.expected(), s.score(), now,
                            AnomalyFinding.OPEN, null, null));
                }
            });
        });
        return out;
    }
}
