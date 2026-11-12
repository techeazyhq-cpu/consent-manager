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

import com.techeazyhq.cm.cnscmd.dom.AiDraftingAccess;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingAccessRepoPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link AiDraftingAccessRepoPort} adapter (ADR-114). */
public class R2dbcAiDraftingAccessAdapter implements AiDraftingAccessRepoPort {

    private final DatabaseClient db;

    public R2dbcAiDraftingAccessAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<AiDraftingAccess> find(String dfId) {
        return db.sql("select * from cns_cmd.ai_drafting_access where df_id = :dfId").bind("dfId", dfId).map((row, meta) -> access(row)).one();
    }

    @Override
    public Mono<AiDraftingAccess> save(AiDraftingAccess a) {
        AiDraftingAccess.Request q = a.request();
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.ai_drafting_access (df_id, enabled, request_sts, request_note, requested_by, requested_at, decided_by,
                                                                decided_at, decline_reason, updated_at)
                        values (:dfId, :enabled, :sts, :note, :by, :at, :decidedBy, :decidedAt, :reason, :updatedAt)
                        on conflict (df_id) do update set
                            enabled = excluded.enabled, request_sts = excluded.request_sts, request_note = excluded.request_note,
                            requested_by = excluded.requested_by, requested_at = excluded.requested_at, decided_by = excluded.decided_by,
                            decided_at = excluded.decided_at, decline_reason = excluded.decline_reason, updated_at = excluded.updated_at
                        """)
                .bind("dfId", a.dfId())
                .bind("enabled", a.enabled())
                .bind("sts", q.sts().name())
                .bind("updatedAt", a.updatedAt());
        spec = R2dbcBinds.bindNullable(spec, "note", q.note(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "by", q.requestedBy(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "at", q.requestedAt(), Instant.class);
        spec = R2dbcBinds.bindNullable(spec, "decidedBy", q.decidedBy(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "decidedAt", q.decidedAt(), Instant.class);
        spec = R2dbcBinds.bindNullable(spec, "reason", q.declineReason(), String.class);
        return spec.then().thenReturn(a);
    }

    @Override
    public Flux<AiDraftingAccess> pending() {
        return db.sql("select * from cns_cmd.ai_drafting_access where request_sts = 'PENDING' order by requested_at").map((row, meta) -> access(row)).all();
    }

    @Override
    public Flux<AiDraftingAccess> enabled() {
        return db.sql("select * from cns_cmd.ai_drafting_access where enabled order by updated_at desc").map((row, meta) -> access(row)).all();
    }

    private static AiDraftingAccess access(Readable row) {
        return new AiDraftingAccess(row.get("df_id", String.class), Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                new AiDraftingAccess.Request(AiDraftingAccess.RequestSts.valueOf(row.get("request_sts", String.class)), row.get("request_note", String.class),
                        row.get("requested_by", String.class), row.get("requested_at", Instant.class), row.get("decided_by", String.class),
                        row.get("decided_at", Instant.class), row.get("decline_reason", String.class)),
                row.get("updated_at", Instant.class));
    }
}
