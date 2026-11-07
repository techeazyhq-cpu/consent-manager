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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.dsr.dom.DpReq;
import com.techeazyhq.cm.dsr.dom.DpReqRepoPort;
import com.techeazyhq.cm.dsr.dom.DpRgt;
import com.techeazyhq.cm.dsr.dom.OpenDpReqExistsException;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcDpReqRepoAdapter implements DpReqRepoPort {

    private static final String OPEN = "('OPEN', 'ACKNOWLEDGED')";

    private final DatabaseClient db;

    public R2dbcDpReqRepoAdapter(DatabaseClient db) {
        this.db = db;
    }

    /** A second open request for the same consent and right breaks {@code uq_dp_req_open}. */
    @Override
    public Mono<DpReq> save(DpReq req) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into dsr.dp_req (id, knd, dp_id, df_id, cns_req_id, prps_code, rgt, details, received_at,
                            due_by, sts, acknowledged_at, closed_at, closed_by, closed_by_name, response, refusal_ground)
                        values (:id, :knd, :dpId, :dfId, :cnsReqId, :prpsCode, :rgt, :details, :receivedAt, :dueBy, :sts,
                            :acknowledgedAt, :closedAt, :closedBy, :closedByName, :response, :refusalGround)
                        on conflict (id) do update set
                            sts = excluded.sts,
                            acknowledged_at = excluded.acknowledged_at,
                            closed_at = excluded.closed_at,
                            closed_by = excluded.closed_by,
                            closed_by_name = excluded.closed_by_name,
                            response = excluded.response,
                            refusal_ground = excluded.refusal_ground
                        """)
                .bind("id", req.id())
                .bind("knd", req.knd().name())
                .bind("dpId", req.dpId())
                .bind("receivedAt", req.receivedAt())
                .bind("dueBy", req.dueBy())
                .bind("sts", req.sts().name());
        spec = bindNullable(spec, "dfId", req.dfId(), String.class);
        spec = bindNullable(spec, "cnsReqId", req.cnsReqId(), String.class);
        spec = bindNullable(spec, "prpsCode", req.prpsCode(), String.class);
        spec = bindNullable(spec, "rgt", req.rgt() == null ? null : req.rgt().name(), String.class);
        spec = bindNullable(spec, "details", req.details(), String.class);
        spec = bindNullable(spec, "acknowledgedAt", req.acknowledgedAt(), Instant.class);
        spec = bindNullable(spec, "closedAt", req.closedAt(), Instant.class);
        spec = bindNullable(spec, "closedBy", req.closedBy(), String.class);
        spec = bindNullable(spec, "closedByName", req.closedByName(), String.class);
        spec = bindNullable(spec, "response", req.response(), String.class);
        spec = bindNullable(spec, "refusalGround", req.refusalGround(), String.class);
        return spec.then()
                .onErrorMap(e -> e instanceof DataIntegrityViolationException
                                || e instanceof R2dbcDataIntegrityViolationException,
                        e -> new OpenDpReqExistsException())
                .thenReturn(req);
    }

    @Override
    public Mono<DpReq> findById(String id) {
        return db.sql("select * from dsr.dp_req where id = :id")
                .bind("id", id)
                .map(R2dbcDpReqRepoAdapter::row)
                .one();
    }

    @Override
    public Flux<DpReq> findByDpId(String dpId, int limit) {
        return db.sql("select * from dsr.dp_req where dp_id = :dpId order by received_at desc limit :limit")
                .bind("dpId", dpId)
                .bind("limit", limit)
                .map(R2dbcDpReqRepoAdapter::row)
                .all();
    }

    @Override
    public Flux<DpReq> findOpenByDpId(String dpId) {
        return db.sql("select * from dsr.dp_req where dp_id = :dpId and sts in " + OPEN)
                .bind("dpId", dpId)
                .map(R2dbcDpReqRepoAdapter::row)
                .all();
    }

    @Override
    public Mono<Long> eraseTextOf(String dpId) {
        return db.sql("""
                        update dsr.dp_req set details = null, response = null
                        where dp_id = :dpId and (details is not null or response is not null)
                        """)
                .bind("dpId", dpId)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public Mono<Long> countOpenByDpId(String dpId) {
        return db.sql("select count(*) as n from dsr.dp_req where dp_id = :dpId and sts in " + OPEN)
                .bind("dpId", dpId)
                .map(row -> row.get("n", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    @Override
    public Flux<DpReq> findByDfId(String dfId, DpReq.Sts sts, int limit) {
        return query("knd = 'RELAY' and df_id = :dfId", sts, limit).bind("dfId", dfId)
                .map(R2dbcDpReqRepoAdapter::row)
                .all();
    }

    @Override
    public Flux<DpReq> findGrievances(DpReq.Sts sts, int limit) {
        return query("knd = 'GRIEVANCE'", sts, limit)
                .map(R2dbcDpReqRepoAdapter::row)
                .all();
    }

    /** Open requests are worked to their deadline, soonest first; closed ones read newest first. */
    private DatabaseClient.GenericExecuteSpec query(String where, DpReq.Sts sts, int limit) {
        if (sts == null) {
            return db.sql("select * from dsr.dp_req where " + where
                            + " order by case when sts in " + OPEN + " then 0 else 1 end, "
                            + "case when sts in " + OPEN + " then due_by end asc, received_at desc limit :limit")
                    .bind("limit", limit);
        }
        String order = sts.open() ? "due_by asc" : "closed_at desc";
        return db.sql("select * from dsr.dp_req where " + where + " and sts = :sts order by " + order + " limit :limit")
                .bind("sts", sts.name())
                .bind("limit", limit);
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name,
                                                                      T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static DpReq row(Readable row) {
        String rgt = row.get("rgt", String.class);
        return DpReq.rehydrate(
                row.get("id", String.class),
                DpReq.Knd.valueOf(row.get("knd", String.class)),
                row.get("dp_id", String.class),
                row.get("df_id", String.class),
                row.get("cns_req_id", String.class),
                row.get("prps_code", String.class),
                rgt == null ? null : DpRgt.valueOf(rgt),
                row.get("details", String.class),
                row.get("received_at", Instant.class),
                row.get("due_by", Instant.class),
                DpReq.Sts.valueOf(row.get("sts", String.class)),
                row.get("acknowledged_at", Instant.class),
                row.get("closed_at", Instant.class),
                row.get("closed_by", String.class),
                row.get("closed_by_name", String.class),
                row.get("response", String.class),
                row.get("refusal_ground", String.class));
    }
}
