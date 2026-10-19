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

package com.techeazyhq.cm.bil.infra;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.MessageSpec;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSender;
import org.apache.pulsar.reactive.client.api.ReactiveMessageSenderCache;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.audkit.PlatformActionEvt;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Billing's outbox (ADR-004, ADR-040): an action is written in the same transaction as the change it describes, and a
 * relay publishes what's waiting to {@code persistent://cm/bil/evt-platform-action-v1}, where the audit ledger reads it.
 */
public class R2dbcBilOutbox {

    private static final Logger LOG = LoggerFactory.getLogger(R2dbcBilOutbox.class);
    static final String TOPIC_NAME = "platform-action";

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;
    private final ReactivePulsarClient pulsar;
    private final ReactiveMessageSenderCache senderCache = AdaptedReactivePulsarClientFactory.createCache();
    private final Map<String, ReactiveMessageSender<String>> senders = new ConcurrentHashMap<>();

    public R2dbcBilOutbox(DatabaseClient db, ObjectMapper objectMapper, ReactivePulsarClient pulsar) {
        this.db = db;
        this.objectMapper = objectMapper;
        this.pulsar = pulsar;
    }

    public Mono<Void> enqueue(PlatformActionEvt evt) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(evt))
                .flatMap(payload -> db.sql("""
                                insert into bil.outbox_evt (id, evt_id, agg_type, agg_id, evt_type, payload_json, created_at)
                                values (:id, :evtId, 'PlatformAction', :aggId, 'PlatformActionEvt', :payload, :createdAt)
                                on conflict (evt_id) do nothing
                                """)
                        .bind("id", UUID.randomUUID())
                        .bind("evtId", evt.evtId())
                        .bind("aggId", evt.target() != null ? evt.target() : evt.ctx())
                        .bind("payload", payload)
                        .bind("createdAt", evt.occurredAt())
                        .then());
    }

    private final AtomicBoolean passRunning = new AtomicBoolean();

    /**
     * One pass at a time, and the batch read in full before publishing. Before, the scheduler started a pass every
     * second whether or not the last had finished, and each pass held the select's connection while it published and
     * each row's update waited for another: a backlog took every connection in the pool and deadlocked the service
     * (found by the load test, docs/security/vapt-and-performance.md). Overlapping passes also published rows twice.
     */
    @Scheduled(fixedDelayString = "${cm.bil.obx-relay.fixed-delay-ms:1000}")
    public void relay() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        db.sql("select id, payload_json from bil.outbox_evt where published_at is null order by created_at limit 100")
                .map((row, meta) -> new Row(row.get("id", UUID.class), row.get("payload_json", String.class)))
                .all()
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(this::publishAndMark)
                .then()
                .doFinally(signal -> passRunning.set(false))
                .subscribe(v -> {
                }, err -> LOG.warn("Outbox relay iteration failed", err));
    }

    private Mono<Void> publishAndMark(Row row) {
        String topic = PulsarTopics.evt("bil", TOPIC_NAME);
        return senders.computeIfAbsent(topic, t -> pulsar.messageSender(Schema.STRING).topic(t).cache(senderCache)
                        .maxInflight(100).build())
                .sendOne(MessageSpec.of(row.payloadJson()))
                .then(db.sql("update bil.outbox_evt set published_at = :now where id = :id")
                        .bind("now", Instant.now()).bind("id", row.id()).then());
    }

    private record Row(UUID id, String payloadJson) {
    }
}
