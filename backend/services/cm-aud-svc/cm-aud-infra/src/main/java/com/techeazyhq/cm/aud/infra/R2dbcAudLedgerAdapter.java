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

package com.techeazyhq.cm.aud.infra;

import com.techeazyhq.cm.aud.dom.AudChain;
import com.techeazyhq.cm.aud.dom.AudDraft;
import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudLedgerPort;
import com.techeazyhq.cm.aud.dom.AudQuery;
import com.techeazyhq.cm.aud.dom.AudRecord;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The ledger in PostgreSQL ({@code aud} schema, ADR-014). Appends are serialised by a row lock on {@code chain_head},
 * so concurrent consumers can never fork the chain; {@code entry} itself is append-only, enforced by a trigger.
 * Payloads and the principal index live in their own tables, the only ones erasure may touch.
 */
public class R2dbcAudLedgerAdapter implements AudLedgerPort {

    private static final String ENTRY_COLUMNS = "e.seq, e.evt_id, e.evt_type, e.occurred_at, e.recorded_at, e.cns_req_id, "
            + "e.cns_art_id, e.df_id, e.payload_sha256, e.prev_hash, e.entry_hash";

    private final DatabaseClient db;
    private final TransactionalOperator tx;

    public R2dbcAudLedgerAdapter(DatabaseClient db, TransactionalOperator tx) {
        this.db = db;
        this.tx = tx;
    }

    private record Head(long seq, String hash) {
    }

    @Override
    public Mono<AudEntry> append(AudDraft draft, Instant recordedAt) {
        Mono<AudEntry> underLock = db.sql("select seq, entry_hash from aud.chain_head where id = 1 for update")
                .map((row, meta) -> new Head(row.get("seq", Long.class), row.get("entry_hash", String.class)))
                .one()
                .flatMap(head -> byEvtId(draft.evtId()).switchIfEmpty(Mono.defer(() -> {
                    AudEntry entry = AudChain.link(head.seq(), head.hash(), draft, recordedAt);
                    return insert(entry, draft).thenReturn(entry);
                })));
        return byEvtId(draft.evtId()).switchIfEmpty(underLock.as(tx::transactional));
    }

    private Mono<Void> insert(AudEntry entry, AudDraft draft) {
        Mono<Void> addEntry = bindNullable(db.sql("""
                        insert into aud.entry (seq, evt_id, evt_type, occurred_at, recorded_at, cns_req_id, cns_art_id, df_id,
                                               payload_sha256, prev_hash, entry_hash)
                        values (:seq, :evtId, :evtType, :occurredAt, :recordedAt, :cnsReqId, :cnsArtId, :dfId,
                                :payloadSha256, :prevHash, :entryHash)""")
                .bind("seq", entry.seq())
                .bind("evtId", entry.evtId())
                .bind("evtType", entry.evtType())
                .bind("occurredAt", entry.occurredAt())
                .bind("recordedAt", entry.recordedAt())
                .bind("payloadSha256", entry.payloadSha256())
                .bind("prevHash", entry.prevHash())
                .bind("entryHash", entry.entryHash()), entry)
                .then();
        Mono<Void> addPayload = db.sql("insert into aud.entry_payload (seq, payload_json) values (:seq, :payload)")
                .bind("seq", entry.seq())
                .bind("payload", draft.payloadJson())
                .then();
        Mono<Void> addSubject = draft.dpId() == null ? Mono.empty()
                : db.sql("insert into aud.entry_subject (seq, dp_id) values (:seq, :dpId)")
                        .bind("seq", entry.seq())
                        .bind("dpId", draft.dpId())
                        .then();
        Mono<Void> moveHead = db.sql("update aud.chain_head set seq = :seq, entry_hash = :hash where id = 1")
                .bind("seq", entry.seq())
                .bind("hash", entry.entryHash())
                .then();
        return addEntry.then(addPayload).then(addSubject).then(moveHead);
    }

    private static DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, AudEntry entry) {
        spec = entry.cnsReqId() == null ? spec.bindNull("cnsReqId", String.class) : spec.bind("cnsReqId", entry.cnsReqId());
        spec = entry.cnsArtId() == null ? spec.bindNull("cnsArtId", String.class) : spec.bind("cnsArtId", entry.cnsArtId());
        return entry.dfId() == null ? spec.bindNull("dfId", String.class) : spec.bind("dfId", entry.dfId());
    }

    private Mono<AudEntry> byEvtId(UUID evtId) {
        return db.sql("select " + ENTRY_COLUMNS + " from aud.entry e where e.evt_id = :evtId")
                .bind("evtId", evtId)
                .map((row, meta) -> toEntry(row))
                .one();
    }

    @Override
    public Flux<AudRecord> records() {
        return db.sql("select " + ENTRY_COLUMNS + ", p.payload_json from aud.entry e "
                        + "left join aud.entry_payload p on p.seq = e.seq order by e.seq")
                .map((row, meta) -> new AudRecord(toEntry(row), row.get("payload_json", String.class)))
                .all();
    }

    @Override
    public Flux<AudEntry> find(AudQuery query) {
        List<String> where = new ArrayList<>();
        if (query.cnsReqId() != null) where.add("e.cns_req_id = :cnsReqId");
        if (query.cnsArtId() != null) where.add("e.cns_art_id = :cnsArtId");
        if (query.dfId() != null) where.add("e.df_id = :dfId");
        if (query.dpId() != null) where.add("exists (select 1 from aud.entry_subject s where s.seq = e.seq and s.dp_id = :dpId)");
        String sql = "select " + ENTRY_COLUMNS + " from aud.entry e"
                + (where.isEmpty() ? "" : " where " + String.join(" and ", where))
                + " order by e.seq desc limit :limit";
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql).bind("limit", query.limit());
        if (query.cnsReqId() != null) spec = spec.bind("cnsReqId", query.cnsReqId());
        if (query.cnsArtId() != null) spec = spec.bind("cnsArtId", query.cnsArtId());
        if (query.dfId() != null) spec = spec.bind("dfId", query.dfId());
        if (query.dpId() != null) spec = spec.bind("dpId", query.dpId());
        return spec.map((row, meta) -> toEntry(row)).all();
    }

    @Override
    public Mono<AudEntry> head() {
        return db.sql("select " + ENTRY_COLUMNS + " from aud.entry e order by e.seq desc limit 1")
                .map((row, meta) -> toEntry(row))
                .one();
    }

    private static AudEntry toEntry(Row row) {
        return new AudEntry(
                row.get("seq", Long.class),
                row.get("evt_id", UUID.class),
                row.get("evt_type", String.class),
                row.get("occurred_at", OffsetDateTime.class).toInstant(),
                row.get("recorded_at", OffsetDateTime.class).toInstant(),
                row.get("cns_req_id", String.class),
                row.get("cns_art_id", String.class),
                row.get("df_id", String.class),
                row.get("payload_sha256", String.class),
                row.get("prev_hash", String.class),
                row.get("entry_hash", String.class));
    }
}
