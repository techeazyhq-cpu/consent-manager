package com.techeazyhq.cm.dfreg.infra;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;

import com.techeazyhq.cm.evtkit.PulsarTopics;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ObxRelay {

    private static final String CTX = "dfreg";
    private static final Map<String, String> EVT_TYPE_TO_TOPIC_NAME = Map.of("DfActivatedEvt", "df-activated");

    private final DatabaseClient db;
    private final PulsarDfEvtProducer producer;

    public ObxRelay(DatabaseClient db, PulsarDfEvtProducer producer) {
        this.db = db;
        this.producer = producer;
    }

    @Scheduled(fixedDelayString = "${cm.dfreg.obx-relay.fixed-delay-ms:1000}")
    public void relayPendingEvents() {
        fetchUnpublished().concatMap(this::publishAndMark).then()
                .subscribe(v -> {
                }, err -> org.slf4j.LoggerFactory.getLogger(ObxRelay.class).warn("Outbox relay iteration failed", err));
    }

    private Flux<OutboxRow> fetchUnpublished() {
        return db.sql("select id, evt_type, payload_json from df_reg.outbox_evt where published_at is null order by created_at limit 100")
                .map((row, meta) -> new OutboxRow(row.get("id", UUID.class), row.get("evt_type", String.class), row.get("payload_json", String.class)))
                .all();
    }

    private Mono<Void> publishAndMark(OutboxRow row) {
        String topicSuffix = EVT_TYPE_TO_TOPIC_NAME.get(row.evtType());
        if (topicSuffix == null) {
            return Mono.empty();
        }
        return producer.publish(PulsarTopics.evt(CTX, topicSuffix), row.payloadJson())
                .then(db.sql("update df_reg.outbox_evt set published_at = :now where id = :id")
                        .bind("now", Instant.now()).bind("id", row.id()).then());
    }

    private record OutboxRow(UUID id, String evtType, String payloadJson) {
    }
}
