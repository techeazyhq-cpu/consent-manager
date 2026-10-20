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

import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.CnsRecording;
import com.techeazyhq.cm.cnscmd.dom.WhkMessage;
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
    private final R2dbcCnsRecRepoAdapter records;
    private final R2dbcWhkRepoAdapter webhooks;

    ObxWriter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
        this.records = new R2dbcCnsRecRepoAdapter(db, objectMapper);
        this.webhooks = new R2dbcWhkRepoAdapter(db, objectMapper);
    }

    /**
     * Writes the event to the outbox and, for a change of a consent, to its 27560 record (ADR-025) and a webhook
     * delivery for each of its fiduciary's endpoints that wants it (ADR-047), all in the caller's transaction: every
     * change passes here, so none goes unrecorded or untold, and a fiduciary is told at least once.
     */
    Mono<Void> insert(DomainEvt evt, String aggType, String aggId) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(evt))
                .map(payload -> ObxRow.pending(evt.evtId(), aggType, aggId, evt.getClass().getSimpleName(), payload))
                .flatMap(this::insertRow)
                .then(Mono.defer(() -> CnsRecording.of(evt).map(records::record).orElse(Mono.empty())))
                .then(Mono.defer(() -> WhkMessage.of(evt).map(webhooks::enqueue).orElse(Mono.empty())));
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
