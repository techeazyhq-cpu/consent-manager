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

package com.techeazyhq.cm.dsr.infra;

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
import com.techeazyhq.cm.dsr.dom.DpReqEvtPort;
import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Principal Requests' outbox (ADR-004): written in the same transaction as the change it describes, then relayed to
 * Pulsar. Two kinds of row:
 * <ul>
 * <li>actions, to {@code evt-platform-action}, where the audit ledger reads them (ADR-040);</li>
 * <li>messages for a fiduciary, to {@code evt-df-message}, which cm-cns-cmd-svc hands to the fiduciary's webhooks
 * (ADR-047, ADR-058).</li>
 * </ul>
 */
public class R2dbcDsrOutbox implements DpReqEvtPort {

    private static final Logger LOG = LoggerFactory.getLogger(R2dbcDsrOutbox.class);
    static final String CTX = "dsr";
    static final String ACTION_TOPIC = "platform-action";
    static final String DF_MESSAGE_TOPIC = "df-message";
    static final String ACTION_TYPE = "PlatformActionEvt";
    static final String DF_MESSAGE_TYPE = "DfMessage";

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;
    private final ReactivePulsarClient pulsar;
    private final ReactiveMessageSenderCache senderCache = AdaptedReactivePulsarClientFactory.createCache();
    private final Map<String, ReactiveMessageSender<String>> senders = new ConcurrentHashMap<>();
    private final AtomicBoolean passRunning = new AtomicBoolean();

    public R2dbcDsrOutbox(DatabaseClient db, ObjectMapper objectMapper, ReactivePulsarClient pulsar) {
        this.db = db;
        this.objectMapper = objectMapper;
        this.pulsar = pulsar;
    }

    /** An audited action (ADR-040). */
    public Mono<Void> enqueue(PlatformActionEvt evt) {
        return insert(evt.evtId().toString(), "PlatformAction", evt.target() != null ? evt.target() : evt.ctx(),
                ACTION_TYPE, evt, evt.occurredAt());
    }

    @Override
    public Mono<Void> publish(DfMessage message) {
        String aggId = String.valueOf(message.data().getOrDefault("requestId", message.dfId()));
        return insert(message.id(), "DpReq", aggId, DF_MESSAGE_TYPE, message, message.occurredAt());
    }

    private Mono<Void> insert(String evtId, String aggType, String aggId, String evtType, Object body, Instant at) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(body))
                .flatMap(payload -> db.sql("""
                                insert into dsr.outbox_evt (id, evt_id, agg_type, agg_id, evt_type, payload_json, created_at)
                                values (:id, :evtId, :aggType, :aggId, :evtType, :payload, :createdAt)
                                on conflict (evt_id) do nothing
                                """)
                        .bind("id", UUID.randomUUID())
                        .bind("evtId", UUID.fromString(evtId))
                        .bind("aggType", aggType)
                        .bind("aggId", aggId)
                        .bind("evtType", evtType)
                        .bind("payload", payload)
                        .bind("createdAt", at)
                        .then());
    }

    /** One pass at a time, the batch read in full before publishing (the lesson of V-03, vapt-and-performance.md). */
    @Scheduled(fixedDelayString = "${cm.dsr.obx-relay.fixed-delay-ms:1000}")
    public void relay() {
        if (!passRunning.compareAndSet(false, true)) {
            return;
        }
        db.sql("""
                        select id, evt_type, payload_json from dsr.outbox_evt where published_at is null
                        order by created_at limit 100
                        """)
                .map((row, meta) -> new Row(row.get("id", UUID.class), row.get("evt_type", String.class),
                        row.get("payload_json", String.class)))
                .all()
                .collectList()
                .flatMapMany(Flux::fromIterable)
                .concatMap(this::publishAndMark)
                .then()
                .doFinally(signal -> passRunning.set(false))
                .subscribe(v -> {
                }, err -> LOG.warn("[CM-OUTBOX-RELAY-FAILED] Outbox relay iteration failed", err));
    }

    private Mono<Void> publishAndMark(Row row) {
        String topic = PulsarTopics.evt(CTX, DF_MESSAGE_TYPE.equals(row.evtType()) ? DF_MESSAGE_TOPIC : ACTION_TOPIC);
        return senders.computeIfAbsent(topic, t -> pulsar.messageSender(Schema.STRING).topic(t).cache(senderCache)
                        .maxInflight(100).build())
                .sendOne(MessageSpec.of(row.payloadJson()))
                .then(db.sql("update dsr.outbox_evt set published_at = :now where id = :id")
                        .bind("now", Instant.now()).bind("id", row.id()).then());
    }

    private record Row(UUID id, String evtType, String payloadJson) {
    }
}
