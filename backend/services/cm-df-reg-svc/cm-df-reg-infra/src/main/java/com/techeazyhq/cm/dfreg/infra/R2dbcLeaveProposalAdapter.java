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


package com.techeazyhq.cm.dfreg.infra;

import java.time.Instant;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.dfreg.dom.LeaveProposal;
import com.techeazyhq.cm.dfreg.dom.LeaveProposalRepoPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Proposals to end or keep a fiduciary's contract, in df-reg's own schema (ADR-064). */
public class R2dbcLeaveProposalAdapter implements LeaveProposalRepoPort {

    private final DatabaseClient db;

    public R2dbcLeaveProposalAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<LeaveProposal> save(LeaveProposal p) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into df_reg.leave_proposal (id, df_id, kind, reason, proposed_by, proposed_at, status, decided_by, decided_at, decision_note)
                        values (:id, :dfId, :kind, :reason, :by, :at, :status, :decidedBy, :decidedAt, :note)
                        on conflict (id) do update set status = excluded.status, decided_by = excluded.decided_by,
                            decided_at = excluded.decided_at, decision_note = excluded.decision_note
                        """)
                .bind("id", p.id()).bind("dfId", p.dfId()).bind("kind", p.kind().name()).bind("reason", p.reason())
                .bind("by", p.proposedBy()).bind("at", p.proposedAt()).bind("status", p.status().name());
        spec = p.decidedBy() == null ? spec.bindNull("decidedBy", String.class) : spec.bind("decidedBy", p.decidedBy());
        spec = p.decidedAt() == null ? spec.bindNull("decidedAt", Instant.class) : spec.bind("decidedAt", p.decidedAt());
        spec = p.decisionNote() == null ? spec.bindNull("note", String.class) : spec.bind("note", p.decisionNote());
        return spec.then().thenReturn(p);
    }

    @Override
    public Mono<LeaveProposal> findById(String id) {
        return db.sql("select * from df_reg.leave_proposal where id = :id").bind("id", id).map(R2dbcLeaveProposalAdapter::row).one();
    }

    @Override
    public Mono<LeaveProposal> findPendingByDfId(String dfId) {
        return db.sql("select * from df_reg.leave_proposal where df_id = :dfId and status = 'PENDING'").bind("dfId", dfId)
                .map(R2dbcLeaveProposalAdapter::row).one();
    }

    @Override
    public Flux<LeaveProposal> find(LeaveProposal.Status status, String dfId) {
        return (dfId == null
                ? db.sql("select * from df_reg.leave_proposal where status = :status order by proposed_at desc limit 200")
                        .bind("status", status.name())
                : db.sql("select * from df_reg.leave_proposal where status = :status and df_id = :dfId order by proposed_at desc limit 200")
                        .bind("status", status.name()).bind("dfId", dfId))
                .map(R2dbcLeaveProposalAdapter::row).all();
    }

    private static LeaveProposal row(Readable r) {
        return LeaveProposal.rehydrate(r.get("id", String.class), r.get("df_id", String.class),
                LeaveProposal.Kind.valueOf(r.get("kind", String.class)), r.get("reason", String.class),
                r.get("proposed_by", String.class), r.get("proposed_at", Instant.class),
                LeaveProposal.Status.valueOf(r.get("status", String.class)), r.get("decided_by", String.class),
                r.get("decided_at", Instant.class), r.get("decision_note", String.class));
    }
}
