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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.CnsRcpt;
import com.techeazyhq.cm.cnscmd.dom.CnsRec;
import com.techeazyhq.cm.cnscmd.dom.CnsRecEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsRecRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsRecording;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerRef;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link CnsRecRepoPort} adapter (ADR-025). Called by {@link ObxWriter} for every consent event, inside the change's
 * transaction. An event's place in its record is taken under a transaction-scoped lock on the record, so two changes
 * of one consent never take the same place.
 */
public class R2dbcCnsRecRepoAdapter implements CnsRecRepoPort {

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;

    public R2dbcCnsRecRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> record(CnsRecording.Entry entry) {
        return switch (entry) {
            case CnsRecording.Open open -> open(open.header()).then(append(open.cnsReqId(), open.first()));
            case CnsRecording.Append add -> append(add.cnsReqId(), add.evt());
            case CnsRecording.Subject subject -> db.sql("""
                            update cns_cmd.cns_rec set dp_id = :dpId where cns_req_id = :cnsReqId and dp_id is null
                            """)
                    .bind("dpId", subject.dpId())
                    .bind("cnsReqId", subject.cnsReqId())
                    .then();
        };
    }

    private Mono<Void> open(CnsRec rec) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.cns_rec (id, cns_req_id, df_id, dp_id, prps_code, prps_ver_id, prps_ver_no,
                                                     ntc_lang, ntc_hash, created_at)
                        values (:id, :cnsReqId, :dfId, :dpId, :prpsCode, :prpsVerId, :prpsVerNo, :ntcLang, :ntcHash,
                                :createdAt)
                        on conflict (cns_req_id) do nothing
                        """)
                .bind("id", rec.id())
                .bind("cnsReqId", rec.cnsReqId())
                .bind("dfId", rec.dfId())
                .bind("prpsCode", rec.prpsCode())
                .bind("createdAt", rec.createdAt());
        PrpsVerRef ver = rec.prpsVer();
        spec = R2dbcBinds.bindNullable(spec, "dpId", rec.dpId(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "prpsVerId", ver == null ? null : ver.prpsVerId(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "prpsVerNo", ver == null ? null : ver.verNo(), Integer.class);
        spec = R2dbcBinds.bindNullable(spec, "ntcLang", ver == null ? null : ver.ntcLang(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "ntcHash", ver == null ? null : ver.ntcHash(), String.class);
        return spec.then();
    }

    /** Appends at the record's next place; an event already recorded (a redelivery) is ignored. */
    private Mono<Void> append(String cnsReqId, CnsRecEvt evt) {
        Mono<Void> lock = db.sql("select pg_advisory_xact_lock(hashtext(:key))")
                .bind("key", "cns_rec:" + cnsReqId)
                .then();
        DatabaseClient.GenericExecuteSpec insert = db.sql("""
                        insert into cns_cmd.cns_rec_evt (rec_id, seq, evt_id, occurred_at, type, actor, d_cats,
                                                         valid_from, valid_until, detail)
                        select c.id, coalesce((select max(seq) from cns_cmd.cns_rec_evt e where e.rec_id = c.id), 0) + 1,
                               :evtId, :occurredAt, :type, :actor, cast(:dCats as jsonb), :validFrom, :validUntil, :detail
                        from cns_cmd.cns_rec c
                        where c.cns_req_id = :cnsReqId
                        on conflict (evt_id) do nothing
                        """)
                .bind("cnsReqId", cnsReqId)
                .bind("evtId", evt.evtId())
                .bind("occurredAt", evt.occurredAt())
                .bind("type", evt.type().name())
                .bind("actor", evt.actor().name());
        insert = R2dbcBinds.bindNullable(insert, "dCats", evt.dCats() == null ? null : json(evt.dCats()), String.class);
        insert = R2dbcBinds.bindNullable(insert, "validFrom", evt.validFrom(), Instant.class);
        insert = R2dbcBinds.bindNullable(insert, "validUntil", evt.validUntil(), Instant.class);
        insert = R2dbcBinds.bindNullable(insert, "detail", evt.detail(), String.class);
        return lock.then(insert.then());
    }

    @Override
    public Mono<CnsRec> findByCnsReqId(String cnsReqId) {
        return db.sql("select * from cns_cmd.cns_rec where cns_req_id = :cnsReqId")
                .bind("cnsReqId", cnsReqId)
                .map((row, meta) -> header(row))
                .one()
                .flatMap(this::withEvents);
    }

    @Override
    public Flux<CnsRec> findBySubject(String dpId) {
        return db.sql("select * from cns_cmd.cns_rec where dp_id = :dpId order by created_at desc")
                .bind("dpId", dpId)
                .map((row, meta) -> header(row))
                .all()
                .concatMap(this::withEvents);
    }

    @Override
    public Flux<Pending> pendingReceipts(int limit) {
        return db.sql("""
                        select c.cns_req_id, e.seq
                        from cns_cmd.cns_rec_evt e
                        join cns_cmd.cns_rec c on c.id = e.rec_id
                        left join cns_cmd.cns_rcpt r on r.rec_id = e.rec_id and r.seq = e.seq
                        where r.id is null
                        order by e.occurred_at
                        limit :limit
                        """)
                .bind("limit", limit)
                .map((row, meta) -> new Pending(row.get("cns_req_id", String.class), row.get("seq", Integer.class)))
                .all();
    }

    @Override
    public Mono<CnsRcpt> saveReceipt(CnsRcpt rcpt) {
        return db.sql("""
                        insert into cns_cmd.cns_rcpt (id, rec_id, seq, issued_at, kid, jws, sha256)
                        values (:id, :recId, :seq, :issuedAt, :kid, :jws, :sha256)
                        """)
                .bind("id", rcpt.id())
                .bind("recId", rcpt.recId())
                .bind("seq", rcpt.seq())
                .bind("issuedAt", rcpt.issuedAt())
                .bind("kid", rcpt.kid())
                .bind("jws", rcpt.jws())
                .bind("sha256", rcpt.sha256())
                .then()
                .thenReturn(rcpt);
    }

    @Override
    public Flux<CnsRcpt> receipts(String recId) {
        return db.sql("""
                        select r.*, c.cns_req_id from cns_cmd.cns_rcpt r join cns_cmd.cns_rec c on c.id = r.rec_id
                        where r.rec_id = :recId order by r.seq
                        """)
                .bind("recId", recId)
                .map((row, meta) -> new CnsRcpt(row.get("id", String.class), row.get("rec_id", String.class),
                        row.get("cns_req_id", String.class), row.get("seq", Integer.class),
                        row.get("issued_at", Instant.class), row.get("kid", String.class), row.get("jws", String.class),
                        row.get("sha256", String.class)))
                .all();
    }

    private Mono<CnsRec> withEvents(CnsRec header) {
        return db.sql("select * from cns_cmd.cns_rec_evt where rec_id = :recId order by seq")
                .bind("recId", header.id())
                .map((row, meta) -> evt(row))
                .all()
                .collectList()
                .map(events -> new CnsRec(header.id(), header.cnsReqId(), header.dfId(), header.dpId(), header.prpsCode(),
                        header.prpsVer(), header.createdAt(), events));
    }

    private static CnsRec header(Readable row) {
        String verId = row.get("prps_ver_id", String.class);
        Integer verNo = row.get("prps_ver_no", Integer.class);
        PrpsVerRef ver = verId == null ? null : new PrpsVerRef(verId, verNo == null ? 0 : verNo,
                row.get("ntc_lang", String.class), row.get("ntc_hash", String.class));
        return new CnsRec(row.get("id", String.class), row.get("cns_req_id", String.class), row.get("df_id", String.class),
                row.get("dp_id", String.class), row.get("prps_code", String.class), ver,
                row.get("created_at", Instant.class), List.of());
    }

    private CnsRecEvt evt(Readable row) {
        String dCats = row.get("d_cats", String.class);
        return new CnsRecEvt(row.get("seq", Integer.class), row.get("evt_id", UUID.class),
                row.get("occurred_at", Instant.class), CnsRecEvt.Type.valueOf(row.get("type", String.class)),
                CnsRecEvt.Actor.valueOf(row.get("actor", String.class)), dCats == null ? null : read(dCats),
                row.get("valid_from", Instant.class), row.get("valid_until", Instant.class),
                row.get("detail", String.class));
    }

    private String json(Set<String> codes) {
        try {
            return objectMapper.writeValueAsString(new ArrayList<>(codes).stream().sorted().toList());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write a record's data categories", e);
        }
    }

    private Set<String> read(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read a record's data categories", e);
        }
    }
}
