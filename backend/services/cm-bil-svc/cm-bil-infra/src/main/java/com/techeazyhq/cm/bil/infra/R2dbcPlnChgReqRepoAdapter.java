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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.bil.dom.PendingPlnChgReqExistsException;
import com.techeazyhq.cm.bil.dom.PlnChgReq;
import com.techeazyhq.cm.bil.dom.PlnChgReqRepoPort;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcPlnChgReqRepoAdapter implements PlnChgReqRepoPort {

    private final DatabaseClient db;

    public R2dbcPlnChgReqRepoAdapter(DatabaseClient db) {
        this.db = db;
    }

    /** A second waiting request for the same fiduciary breaks {@code uq_pln_chg_req_pending}. */
    @Override
    public Mono<PlnChgReq> save(PlnChgReq req) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into bil.pln_chg_req (id, df_id, from_pln_name, to_pln_name, note, requested_by,
                            requested_by_name, requested_at, sts, decided_by, decided_by_name, decided_at, decision_note)
                        values (:id, :dfId, :fromPln, :toPln, :note, :requestedBy, :requestedByName, :requestedAt, :sts,
                            :decidedBy, :decidedByName, :decidedAt, :decisionNote)
                        on conflict (id) do update set
                            sts = excluded.sts,
                            decided_by = excluded.decided_by,
                            decided_by_name = excluded.decided_by_name,
                            decided_at = excluded.decided_at,
                            decision_note = excluded.decision_note
                        """)
                .bind("id", req.id())
                .bind("dfId", req.dfId())
                .bind("fromPln", req.fromPlnName())
                .bind("toPln", req.toPlnName())
                .bind("requestedBy", req.requestedBy())
                .bind("requestedAt", req.requestedAt())
                .bind("sts", req.sts().name());
        spec = bindNullable(spec, "note", req.note(), String.class);
        spec = bindNullable(spec, "requestedByName", req.requestedByName(), String.class);
        spec = bindNullable(spec, "decidedBy", req.decidedBy(), String.class);
        spec = bindNullable(spec, "decidedByName", req.decidedByName(), String.class);
        spec = bindNullable(spec, "decidedAt", req.decidedAt(), Instant.class);
        spec = bindNullable(spec, "decisionNote", req.decisionNote(), String.class);
        return spec.then()
                .onErrorMap(e -> e instanceof DataIntegrityViolationException
                                || e instanceof R2dbcDataIntegrityViolationException,
                        e -> new PendingPlnChgReqExistsException(req.dfId()))
                .thenReturn(req);
    }

    @Override
    public Mono<PlnChgReq> findById(String id) {
        return db.sql("select * from bil.pln_chg_req where id = :id")
                .bind("id", id)
                .map(R2dbcPlnChgReqRepoAdapter::row)
                .one();
    }

    @Override
    public Mono<PlnChgReq> findPendingByDfId(String dfId) {
        return db.sql("select * from bil.pln_chg_req where df_id = :dfId and sts = 'PENDING'")
                .bind("dfId", dfId)
                .map(R2dbcPlnChgReqRepoAdapter::row)
                .one();
    }

    @Override
    public Flux<PlnChgReq> findByDfId(String dfId, int limit) {
        return db.sql("select * from bil.pln_chg_req where df_id = :dfId order by requested_at desc limit :limit")
                .bind("dfId", dfId)
                .bind("limit", limit)
                .map(R2dbcPlnChgReqRepoAdapter::row)
                .all();
    }

    @Override
    public Flux<PlnChgReq> findBySts(PlnChgReq.Sts sts, int limit) {
        if (sts == null) {
            return db.sql("select * from bil.pln_chg_req order by requested_at desc limit :limit")
                    .bind("limit", limit)
                    .map(R2dbcPlnChgReqRepoAdapter::row)
                    .all();
        }
        // the queue is worked oldest first; decided requests read newest first
        String order = sts == PlnChgReq.Sts.PENDING ? "asc" : "desc";
        return db.sql("select * from bil.pln_chg_req where sts = :sts order by requested_at " + order + " limit :limit")
                .bind("sts", sts.name())
                .bind("limit", limit)
                .map(R2dbcPlnChgReqRepoAdapter::row)
                .all();
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name,
                                                                      T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static PlnChgReq row(Readable row) {
        return PlnChgReq.rehydrate(
                row.get("id", String.class),
                row.get("df_id", String.class),
                row.get("from_pln_name", String.class),
                row.get("to_pln_name", String.class),
                row.get("note", String.class),
                row.get("requested_by", String.class),
                row.get("requested_by_name", String.class),
                row.get("requested_at", Instant.class),
                PlnChgReq.Sts.valueOf(row.get("sts", String.class)),
                row.get("decided_by", String.class),
                row.get("decided_by_name", String.class),
                row.get("decided_at", Instant.class),
                row.get("decision_note", String.class));
    }
}
