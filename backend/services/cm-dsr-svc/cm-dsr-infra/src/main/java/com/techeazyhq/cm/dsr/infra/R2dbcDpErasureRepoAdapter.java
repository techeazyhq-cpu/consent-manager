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
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dsr.dom.DpErasure;
import com.techeazyhq.cm.dsr.dom.DpErasurePorts;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcDpErasureRepoAdapter implements DpErasurePorts.Repo {

    private static final TypeReference<List<String>> IDS = new TypeReference<>() {
    };

    private final DatabaseClient db;
    private final ObjectMapper json;

    public R2dbcDpErasureRepoAdapter(DatabaseClient db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** A second erasure under way for the same principal breaks {@code uq_dp_erasure_running}. */
    @Override
    public Mono<DpErasure> save(DpErasure e) {
        return Mono.fromCallable(() -> json.writeValueAsString(e.cnsReqIds()))
                .flatMap(ids -> {
                    DatabaseClient.GenericExecuteSpec spec = db.sql("""
                                    insert into dsr.dp_erasure (id, dp_id, email, requested_at, sts, step, attempts,
                                        dispatched_at, finished_at, last_error, cns_req_ids, consents_withdrawn)
                                    values (:id, :dpId, :email, :requestedAt, :sts, :step, :attempts, :dispatchedAt,
                                        :finishedAt, :lastError, :ids, :withdrawn)
                                    on conflict (id) do update set
                                        email = excluded.email,
                                        sts = excluded.sts,
                                        step = excluded.step,
                                        attempts = excluded.attempts,
                                        dispatched_at = excluded.dispatched_at,
                                        finished_at = excluded.finished_at,
                                        last_error = excluded.last_error,
                                        cns_req_ids = excluded.cns_req_ids,
                                        consents_withdrawn = excluded.consents_withdrawn
                                    """)
                            .bind("id", e.id())
                            .bind("dpId", e.dpId())
                            .bind("requestedAt", e.requestedAt())
                            .bind("sts", e.sts().name())
                            .bind("attempts", e.attempts())
                            .bind("ids", ids)
                            .bind("withdrawn", e.consentsWithdrawn());
                    spec = nullable(spec, "email", e.email(), String.class);
                    spec = nullable(spec, "step", e.step() == null ? null : e.step().name(), String.class);
                    spec = nullable(spec, "dispatchedAt", e.dispatchedAt(), Instant.class);
                    spec = nullable(spec, "finishedAt", e.finishedAt(), Instant.class);
                    spec = nullable(spec, "lastError", e.lastError(), String.class);
                    return spec.then();
                })
                .onErrorMap(x -> x instanceof DataIntegrityViolationException
                                || x instanceof R2dbcDataIntegrityViolationException,
                        x -> new DpErasurePorts.ErasureRunningException())
                .thenReturn(e);
    }

    @Override
    public Mono<DpErasure> findById(String id) {
        return db.sql("select * from dsr.dp_erasure where id = :id").bind("id", id).map(this::row).one();
    }

    @Override
    public Mono<DpErasure> findLatestByDpId(String dpId) {
        return db.sql("select * from dsr.dp_erasure where dp_id = :dpId order by requested_at desc limit 1")
                .bind("dpId", dpId).map(this::row).one();
    }

    @Override
    public Flux<DpErasure> findRunning(int limit) {
        return db.sql("select * from dsr.dp_erasure where sts = 'RUNNING' order by dispatched_at nulls first limit :limit")
                .bind("limit", limit).map(this::row).all();
    }

    @Override
    public Flux<DpErasure> findBySts(DpErasure.Sts sts, int limit) {
        if (sts == null) {
            return db.sql("select * from dsr.dp_erasure order by requested_at desc limit :limit")
                    .bind("limit", limit).map(this::row).all();
        }
        return db.sql("select * from dsr.dp_erasure where sts = :sts order by requested_at desc limit :limit")
                .bind("sts", sts.name()).bind("limit", limit).map(this::row).all();
    }

    private static <T> DatabaseClient.GenericExecuteSpec nullable(DatabaseClient.GenericExecuteSpec spec, String name,
                                                                  T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private DpErasure row(Readable r) {
        String step = r.get("step", String.class);
        List<String> ids;
        try {
            String raw = r.get("cns_req_ids", String.class);
            ids = raw == null ? List.of() : json.readValue(raw, IDS);
        } catch (Exception e) {
            throw new IllegalStateException("Unreadable cns_req_ids", e);
        }
        return DpErasure.rehydrate(
                r.get("id", String.class),
                r.get("dp_id", String.class),
                r.get("email", String.class),
                r.get("requested_at", Instant.class),
                DpErasure.Sts.valueOf(r.get("sts", String.class)),
                step == null ? null : DpErasure.Step.valueOf(step),
                r.get("attempts", Integer.class),
                r.get("dispatched_at", Instant.class),
                r.get("finished_at", Instant.class),
                r.get("last_error", String.class),
                ids,
                r.get("consents_withdrawn", Integer.class));
    }
}
