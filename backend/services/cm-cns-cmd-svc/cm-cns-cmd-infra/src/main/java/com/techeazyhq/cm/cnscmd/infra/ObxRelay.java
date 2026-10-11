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

package com.techeazyhq.cm.cnscmd.infra;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Outbox relay (plan §7c/§12): polls {@code outbox_evt} rows where {@code published_at is null},
 * publishes each to its Pulsar topic, marks it published. Runs on a fixed delay rather than
 * pushing from the same transaction as the aggregate save — decouples "committed to Postgres"
 * from "published to Pulsar" so a broker outage never blocks a consent decision.
 */
public class ObxRelay {

    private static final String CTX = "cnscmd";
    private static final Map<String, String> EVT_TYPE_TO_TOPIC_NAME = Map.of(
            "CnsReqCreatedEvt", "cns-req-created",
            "CnsGrantedEvt", "cns-granted",
            "CnsDeniedEvt", "cns-denied",
            "CnsRevokedEvt", "cns-revoked",
            "CnsDpLinkedEvt", "cns-dp-linked",
            "CnsExpiredEvt", "cns-expired",
            "CnsNotificationFailedEvt", "cns-notification-failed");

    private final DatabaseClient db;
    private final PulsarCnsEvtProducer producer;

    public ObxRelay(DatabaseClient db, PulsarCnsEvtProducer producer) {
        this.db = db;
        this.producer = producer;
    }

    @Scheduled(fixedDelayString = "${cm.ntf.obx-relay.fixed-delay-ms:1000}")
    public void relayPendingEvents() {
        fetchUnpublished()
                .concatMap(this::publishAndMark)
                .then()
                .subscribe(v -> {
                }, err -> org.slf4j.LoggerFactory.getLogger(ObxRelay.class).warn("Outbox relay iteration failed", err));
    }

    private Flux<OutboxRow> fetchUnpublished() {
        return db.sql("select id, evt_type, payload_json from cns_cmd.outbox_evt where published_at is null order by created_at limit 100")
                .map((row, meta) -> new OutboxRow(
                        row.get("id", UUID.class), row.get("evt_type", String.class), row.get("payload_json", String.class)))
                .all();
    }

    private Mono<Void> publishAndMark(OutboxRow row) {
        String topicSuffix = EVT_TYPE_TO_TOPIC_NAME.get(row.evtType());
        if (topicSuffix == null) {
            org.slf4j.LoggerFactory.getLogger(ObxRelay.class).warn("Unknown outbox evt_type {}, skipping", row.evtType());
            return Mono.empty();
        }
        String topic = PulsarTopics.evt(CTX, topicSuffix);
        return producer.publish(topic, row.payloadJson())
                .then(db.sql("update cns_cmd.outbox_evt set published_at = :now where id = :id")
                        .bind("now", Instant.now())
                        .bind("id", row.id())
                        .then());
    }

    private record OutboxRow(UUID id, String evtType, String payloadJson) {
    }
}
