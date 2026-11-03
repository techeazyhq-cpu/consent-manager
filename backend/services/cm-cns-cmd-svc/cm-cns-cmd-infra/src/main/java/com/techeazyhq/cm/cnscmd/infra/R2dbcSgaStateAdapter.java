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

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.cnscmd.dom.SgaStatePort;
import com.techeazyhq.cm.cnscmd.dom.SgaStep;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link SgaStatePort} adapter over {@code cns_cmd.sga_state} (ADR-005). */
public class R2dbcSgaStateAdapter implements SgaStatePort {

    /** Enough to tell what went wrong; a long chain of causes adds nothing an operator can use. */
    private static final int MAX_ERROR_LENGTH = 2000;

    private final DatabaseClient db;

    public R2dbcSgaStateAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Void> started(SgaStep step, Instant at) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.sga_state (saga_id, cns_id, step, df_id, outcome, status, attempts, updated_at)
                        values (:sagaId, :cnsId, :step, :dfId, :outcome, 'STARTED', 1, :at)
                        on conflict (saga_id) do update set
                            status = 'STARTED',
                            attempts = cns_cmd.sga_state.attempts + 1,
                            updated_at = excluded.updated_at
                        """)
                .bind("sagaId", step.sagaId())
                .bind("cnsId", step.cnsId())
                .bind("step", step.typ().name())
                .bind("at", at);
        spec = R2dbcBinds.bindNullable(spec, "dfId", step.dfId(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "outcome", step.outcome(), String.class);
        return spec.then();
    }

    @Override
    public Mono<Void> done(String sagaId, Instant at) {
        return db.sql("update cns_cmd.sga_state set status = 'DONE', last_error = null, updated_at = :at where saga_id = :id")
                .bind("at", at).bind("id", sagaId).then();
    }

    @Override
    public Mono<Void> failed(String sagaId, String error, Instant at) {
        String message = error == null ? "unknown error"
                : error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) : error;
        return db.sql("update cns_cmd.sga_state set status = 'FAILED', last_error = :error, updated_at = :at where saga_id = :id")
                .bind("error", message).bind("at", at).bind("id", sagaId).then();
    }

    @Override
    public Mono<Void> gaveUp(String sagaId, Instant at) {
        return db.sql("update cns_cmd.sga_state set status = 'GAVE_UP', updated_at = :at where saga_id = :id")
                .bind("at", at).bind("id", sagaId).then();
    }

    @Override
    public Flux<SgaStep> findUnfinishedBefore(Instant before, int limit) {
        return db.sql("""
                        select * from cns_cmd.sga_state
                        where status in ('STARTED', 'FAILED') and updated_at < :before
                        order by updated_at limit :limit
                        """)
                .bind("before", before).bind("limit", limit)
                .map((row, meta) -> step(row)).all();
    }

    @Override
    public Mono<SgaStep> findById(String sagaId) {
        return db.sql("select * from cns_cmd.sga_state where saga_id = :id").bind("id", sagaId)
                .map((row, meta) -> step(row)).one();
    }

    private static SgaStep step(Readable row) {
        return new SgaStep(row.get("saga_id", String.class), row.get("cns_id", String.class),
                SgaStep.Typ.valueOf(row.get("step", String.class)), row.get("df_id", String.class),
                row.get("outcome", String.class), SgaStep.Sts.valueOf(row.get("status", String.class)),
                row.get("attempts", Integer.class), row.get("last_error", String.class),
                row.get("updated_at", Instant.class));
    }
}
