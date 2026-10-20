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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.cnscmd.dom.SecretSealerPort;
import com.techeazyhq.cm.cnscmd.dom.WhkDelivery;
import com.techeazyhq.cm.cnscmd.dom.WhkEndpoint;
import com.techeazyhq.cm.cnscmd.dom.WhkRepoPort;
import com.techeazyhq.cm.cnscmd.dom.WhkSenderPort;
import com.techeazyhq.cm.cnscmd.dom.WhkSignature;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Sends webhook deliveries that are due (ADR-047): signs each for its endpoint's secrets in force, posts it, and
 * records the attempt. A 2xx delivers it; anything else is retried on the delivery's schedule and, after the last
 * retry, dead-lettered. One pass at a time; due deliveries are taken under a lease, so a crash mid-send only delays
 * them.
 */
public class WhkDeliveryWorker {

    static final Duration LEASE = Duration.ofSeconds(60);
    static final int BATCH = 50;
    static final int PARALLEL = 8;

    private final WhkRepoPort repo;
    private final WhkSenderPort sender;
    private final SecretSealerPort sealer;
    private final Clock clock;
    private final List<Duration> retryDelays;
    private final AtomicBoolean passRunning = new AtomicBoolean();

    public WhkDeliveryWorker(WhkRepoPort repo, WhkSenderPort sender, SecretSealerPort sealer, Clock clock,
                             List<Duration> retryDelays) {
        this.repo = repo;
        this.sender = sender;
        this.sealer = sealer;
        this.clock = clock;
        this.retryDelays = retryDelays;
    }

    @Scheduled(fixedDelayString = "${cm.webhooks.poll-delay-ms:1000}")
    public void deliverDue() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        pass().doFinally(signal -> passRunning.set(false))
                .subscribe(n -> {
                }, err -> org.slf4j.LoggerFactory.getLogger(WhkDeliveryWorker.class).warn("Webhook pass failed", err));
    }

    /** One pass: every due delivery taken, sent and recorded. Its count. */
    Mono<Long> pass() {
        return repo.claimDue(clock.instant(), LEASE, BATCH)
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .flatMap(d -> deliver(d).thenReturn(d), PARALLEL)
                .count();
    }

    Mono<Void> deliver(WhkDelivery d) {
        int n = d.attempts() + 1;
        return repo.findEndpoint(d.dfId(), d.endpointId())
                .flatMap(endpoint -> endpoint.active() ? send(endpoint, d, n)
                        : Mono.just(new WhkDelivery.Attempt(n, clock.instant(), null, "the endpoint is disabled", 0)))
                .defaultIfEmpty(new WhkDelivery.Attempt(n, clock.instant(), null, "the endpoint was deleted", 0))
                .flatMap(attempt -> repo.saveAttempt(d.after(attempt, retryDelays), attempt));
    }

    private Mono<WhkDelivery.Attempt> send(WhkEndpoint endpoint, WhkDelivery d, int n) {
        Instant at = clock.instant();
        long timestamp = at.getEpochSecond();
        List<String> secrets = endpoint.sealedSecretsAt(at).stream().map(sealer::open).toList();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("webhook-id", d.msgId());
        headers.put("webhook-timestamp", String.valueOf(timestamp));
        headers.put("webhook-signature", WhkSignature.header(secrets, d.msgId(), timestamp, d.payload()));
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "ConsentManager-Webhooks/1");
        long started = System.nanoTime();
        return sender.post(endpoint.url(), headers, d.payload())
                .onErrorResume(e -> Mono.just(new WhkSenderPort.Result(null, e.getClass().getSimpleName())))
                .map(r -> new WhkDelivery.Attempt(n, at, r.status(), r.error(),
                        Duration.ofNanos(System.nanoTime() - started).toMillis()));
    }
}
