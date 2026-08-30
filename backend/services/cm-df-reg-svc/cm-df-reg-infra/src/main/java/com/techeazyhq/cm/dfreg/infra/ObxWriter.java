package com.techeazyhq.cm.dfreg.infra;

import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.domkit.DomainEvt;
import com.techeazyhq.cm.evtkit.ObxRow;

import reactor.core.publisher.Mono;

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
                        insert into df_reg.outbox_evt (id, evt_id, agg_type, agg_id, evt_type, payload_json, created_at, published_at)
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
