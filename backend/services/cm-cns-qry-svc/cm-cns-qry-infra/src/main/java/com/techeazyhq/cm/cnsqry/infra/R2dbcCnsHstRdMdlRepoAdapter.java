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

package com.techeazyhq.cm.cnsqry.infra;

import java.time.Instant;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcCnsHstRdMdlRepoAdapter implements CnsHstRdMdlRepoPort {

    private final DatabaseClient db;

    public R2dbcCnsHstRdMdlRepoAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Void> append(CnsHstRdMdl row) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_qry.cns_hst_rd_mdl (id, cns_req_id, from_sts, to_sts, occurred_at, evt_id, chg)
                        values (:id, :cnsReqId, :fromSts, :toSts, :occurredAt, :evtId, :chg)
                        on conflict (evt_id) do nothing
                        """)
                .bind("id", UUID.randomUUID())
                .bind("cnsReqId", row.cnsReqId())
                .bind("toSts", row.toSts().name())
                .bind("occurredAt", row.occurredAt())
                .bind("evtId", row.evtId());
        spec = row.fromSts() == null ? spec.bindNull("fromSts", String.class) : spec.bind("fromSts", row.fromSts().name());
        spec = row.chg() == null ? spec.bindNull("chg", String.class) : spec.bind("chg", row.chg());
        return spec.then();
    }

    @Override
    public Flux<CnsHstRdMdl> findByCnsReqId(String cnsReqId) {
        return db.sql("select cns_req_id, from_sts, to_sts, occurred_at, evt_id, chg from cns_qry.cns_hst_rd_mdl where cns_req_id = :cnsReqId order by occurred_at")
                .bind("cnsReqId", cnsReqId)
                .map((row, meta) -> new CnsHstRdMdl(
                        row.get("cns_req_id", String.class),
                        row.get("from_sts", String.class) == null ? null : CnsLcySts.valueOf(row.get("from_sts", String.class)),
                        CnsLcySts.valueOf(row.get("to_sts", String.class)),
                        row.get("occurred_at", Instant.class),
                        row.get("evt_id", String.class),
                        row.get("chg", String.class)))
                .all();
    }
}
