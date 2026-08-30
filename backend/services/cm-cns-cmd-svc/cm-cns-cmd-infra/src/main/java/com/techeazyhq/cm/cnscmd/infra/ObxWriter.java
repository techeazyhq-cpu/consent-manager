package com.techeazyhq.cm.cnscmd.infra;

import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.domkit.DomainEvt;
import com.techeazyhq.cm.evtkit.ObxRow;

import reactor.core.publisher.Mono;

/**
 * Shared outbox-row insert, used by both aggregate repo adapters (to drain
 * {@code Agg.pullEvts()} in the same transaction as the aggregate save) and by
 * {@link R2dbcObxAdapter} (for command-handler-composed events like {@code CnsGrantedEvt}).
 * Participates in the ambient R2DBC transaction opened by the {@code @Transactional} command
 * handler — see plan §7c's Outbox pattern note.
 */
final class ObxWriter {

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;

    ObxWriter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    Mono<Void> insert(DomainEvt evt, String aggType, String aggId) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(evt))
                .map(payload -> ObxRow.pending(evt.evtId(), aggType, aggId, evt.getClass().getSimpleName(), payload))
                .flatMap(this::insertRow);
    }

    private Mono<Void> insertRow(ObxRow row) {
        return db.sql("""
                        insert into cns_cmd.outbox_evt (id, evt_id, agg_type, agg_id, evt_type, payload_json, created_at, published_at)
                        values (:id, :evtId, :aggType, :aggId, :evtType, :payload, :createdAt, null)
                        on conflict (evt_id) do nothing
                        """)
                .bind("id", UUID.randomUUID())
                .bind("evtId", row.evtId())
                .bind("aggType", row.aggType())
                .bind("aggId", row.aggId())
                .bind("evtType", row.evtType())
                .bind("payload", row.payloadJson())
                .bind("createdAt", row.createdAt())
                .then();
    }
}
