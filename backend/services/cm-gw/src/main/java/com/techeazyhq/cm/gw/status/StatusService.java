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

package com.techeazyhq.cm.gw.status;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.gw.status.StatusMath.Overall;
import com.techeazyhq.cm.gw.status.StatusMath.Severity;
import com.techeazyhq.cm.gw.status.StatusMath.State;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The platform's public status (ADR-067). Every 30 seconds the gateway asks each service behind it, and sign-in, whether it is
 * up, and counts the answers by hour and by day in Redis (so every gateway replica adds to the same history). The status page
 * shows each component's state, its uptime over the last 24 hours and 30 days, and the notices staff have posted. It shows what
 * a customer can understand and act on (what is working, what is not, since when) and nothing of how it is built: no address,
 * no version, no error.
 */
@Component
public class StatusService {

    private static final Logger LOG = LoggerFactory.getLogger(StatusService.class);
    private static final Duration HOUR_KEPT = Duration.ofDays(2);
    private static final Duration DAY_KEPT = Duration.ofDays(35);
    private static final Duration CACHE = Duration.ofSeconds(15);
    private static final String NOTICES = "status:notices";

    /** What each route is called to a customer. A route not named here is not shown (it is a second door to one already listed). */
    private static final Map<String, String> LABELS = Map.of(
            "df-reg", "Organisation accounts and API keys",
            "cns-cmd", "Consent requests and decisions",
            "cns-qry", "Consent records and verification",
            "ntf", "Notifications",
            "bil", "Plans and billing",
            "aud", "Audit ledger",
            "stf", "Platform administration",
            "dsr", "Principal requests");
    /** A consent cannot be asked for, decided or checked without these. */
    private static final List<String> CRITICAL = List.of("df-reg", "cns-cmd", "cns-qry", "sign-in");

    /** A component the gateway watches: what it is called, where its health is, and what it is to a customer. */
    record Component(String id, String label, URI health) {
    }

    /** A notice staff posted. */
    public record Notice(String id, String title, String body, Severity severity, List<String> componentIds, Instant startedAt,
                         Instant resolvedAt, String postedBy) {
    }

    public record ComponentStatus(String id, String label, State state, Double uptime24h, Double uptime30d) {
    }

    public record Report(Overall overall, Instant updatedAt, List<ComponentStatus> components, List<Notice> notices) {
    }

    private final RouteDefinitionLocator routes;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json;
    private final WebClient client;
    private final String signInHealth;
    private final List<Component> components = new ArrayList<>();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final AtomicReference<Cached> cached = new AtomicReference<>();

    private record Cached(Report report, Instant at) {
    }

    public StatusService(RouteDefinitionLocator routes, ReactiveStringRedisTemplate redis, ObjectMapper json, WebClient.Builder builder,
                         @Value("${cm.security.jwks-base:http://localhost:8080/realms/}") String jwksBase,
                         @Value("${cm.security.realm-prefix:}") String realmPrefix) {
        this.routes = routes;
        this.redis = redis;
        this.json = json;
        this.client = builder.build();
        String base = jwksBase.endsWith("/") ? jwksBase : jwksBase + "/";
        this.signInHealth = base + realmPrefix + "cm-dp/protocol/openid-connect/certs";
    }

    @EventListener(ApplicationReadyEvent.class)
    public void discover() {
        Map<String, Component> found = new LinkedHashMap<>();
        for (RouteDefinition route : routes.getRouteDefinitions().collectList().blockOptional().orElse(List.of())) {
            String label = LABELS.get(route.getId());
            if (label != null) {
                found.put(route.getId(), new Component(route.getId(), label, URI.create(route.getUri().toString() + "/actuator/health")));
            }
        }
        found.put("sign-in", new Component("sign-in", "Sign-in", URI.create(signInHealth)));
        synchronized (components) {
            components.clear();
            components.addAll(found.values());
        }
        LOG.info("Watching {} components for the status page", found.size());
    }

    /** Asks each component whether it is up and counts the answer. A component that does not answer within 3 seconds is down. */
    @Scheduled(fixedDelayString = "${cm.status.probe-interval-ms:30000}", initialDelayString = "${cm.status.probe-initial-delay-ms:15000}")
    public void probe() {
        Flux.fromIterable(snapshot()).flatMap(c -> up(c).flatMap(ok -> record(c, ok)))
                .subscribe(x -> { }, e -> LOG.warn("The status probe failed to record", e));
    }

    private Mono<Boolean> up(Component c) {
        return client.get().uri(c.health()).retrieve().toBodilessEntity().map(r -> r.getStatusCode().is2xxSuccessful())
                .timeout(Duration.ofSeconds(3)).onErrorReturn(false);
    }

    private Mono<Void> record(Component c, boolean ok) {
        failures.compute(c.id(), (k, v) -> ok ? 0 : (v == null ? 0 : v) + 1);
        long now = Instant.now().getEpochSecond();
        return bump(hourKey(c.id(), now / 3600), ok, HOUR_KEPT).then(bump(dayKey(c.id(), now / 86_400), ok, DAY_KEPT));
    }

    private Mono<Void> bump(String key, boolean ok, Duration keep) {
        Mono<Long> total = redis.opsForHash().increment(key, "total", 1);
        Mono<Long> good = ok ? redis.opsForHash().increment(key, "ok", 1) : Mono.just(0L);
        return total.then(good).then(redis.expire(key, keep)).then();
    }

    static String hourKey(String id, long hour) {
        return "status:h:" + id + ":" + hour;
    }

    static String dayKey(String id, long day) {
        return "status:d:" + id + ":" + day;
    }

    private List<Component> snapshot() {
        synchronized (components) {
            return List.copyOf(components);
        }
    }

    /** The public status, at most 15 seconds old. */
    public Mono<Report> report() {
        Cached c = cached.get();
        if (c != null && c.at().plus(CACHE).isAfter(Instant.now())) {
            return Mono.just(c.report());
        }
        return build().doOnNext(r -> cached.set(new Cached(r, Instant.now())));
    }

    private Mono<Report> build() {
        Instant now = Instant.now();
        Mono<List<ComponentStatus>> statuses = Flux.fromIterable(snapshot()).concatMap(c ->
                uptime(c.id(), StatusMath.hoursIn(now, Duration.ofHours(24)), true).zipWith(uptime(c.id(), days(now, 30), false))
                        .map(t -> new ComponentStatus(c.id(), c.label(), StatusMath.stateOf(failures.getOrDefault(c.id(), 0)), t.getT1().orElse(null), t.getT2().orElse(null))))
                .collectList();
        return statuses.zipWith(notices(now)).map(t -> {
            Map<String, State> states = new LinkedHashMap<>();
            t.getT1().forEach(s -> states.put(s.id(), s.state()));
            return new Report(StatusMath.overall(states, CRITICAL), now, t.getT1(), t.getT2());
        });
    }

    private static List<Long> days(Instant now, int count) {
        long today = now.getEpochSecond() / 86_400;
        return java.util.stream.LongStream.range(0, count).mapToObj(i -> today - i).toList();
    }

    private Mono<java.util.Optional<Double>> uptime(String id, List<Long> buckets, boolean hours) {
        return Flux.fromIterable(buckets).flatMap(b -> redis.opsForHash().multiGet(hours ? hourKey(id, b) : dayKey(id, b), List.of("ok", "total")), 16)
                .reduce(new long[2], (sum, values) -> {
                    sum[0] += parse(values.get(0));
                    sum[1] += parse(values.get(1));
                    return sum;
                }).map(sum -> java.util.Optional.ofNullable(StatusMath.uptimePercent(sum[0], sum[1])));
    }

    private static long parse(Object v) {
        return v == null ? 0 : Long.parseLong(v.toString());
    }

    // ------------------------------------------------------------------ notices

    private Mono<List<Notice>> notices(Instant now) {
        return redis.opsForHash().values(NOTICES).map(v -> read(v.toString())).filter(n -> n != null && StatusMath.visible(n.resolvedAt(), now))
                .collectSortedList((a, b) -> b.startedAt().compareTo(a.startedAt()));
    }

    private Notice read(String text) {
        try {
            return json.readValue(text, Notice.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public Set<String> knownIds() {
        Set<String> ids = new java.util.LinkedHashSet<>();
        snapshot().forEach(c -> ids.add(c.id()));
        return ids;
    }

    /** Posts a notice. The caller has checked it. */
    public Mono<Notice> post(String title, String body, Severity severity, List<String> componentIds, String postedBy) {
        Notice n = new Notice(UUID.randomUUID().toString(), title, body, severity, List.copyOf(componentIds), Instant.now(), null, postedBy);
        return write(n).thenReturn(n).doOnSuccess(x -> cached.set(null));
    }

    /** Marks a notice resolved; empty when there is no such notice. */
    public Mono<Notice> resolve(String id) {
        return redis.opsForHash().get(NOTICES, id).map(v -> read(v.toString())).filter(n -> n != null && n.resolvedAt() == null).flatMap(n -> {
            Notice done = new Notice(n.id(), n.title(), n.body(), n.severity(), n.componentIds(), n.startedAt(), Instant.now(), n.postedBy());
            return write(done).thenReturn(done);
        }).doOnSuccess(x -> cached.set(null));
    }

    private Mono<Boolean> write(Notice n) {
        try {
            return redis.opsForHash().put(NOTICES, n.id(), json.writeValueAsString(n));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}
