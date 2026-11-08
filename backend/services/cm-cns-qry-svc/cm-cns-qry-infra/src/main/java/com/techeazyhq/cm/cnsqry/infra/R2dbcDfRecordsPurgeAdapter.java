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

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.techeazyhq.cm.cnsqry.dom.DfRecordsPurgePort;

import reactor.core.publisher.Mono;

/**
 * {@link DfRecordsPurgePort} over the read model: a consent's row and its history go together, once it was last changed
 * before the cut-off and is no longer in force. The read model is rebuilt from events, so this is a copy of what
 * cm-cns-cmd-svc purges at the same time.
 */
public class R2dbcDfRecordsPurgeAdapter implements DfRecordsPurgePort {

    private final DatabaseClient db;
    private final TransactionalOperator tx;

    public R2dbcDfRecordsPurgeAdapter(DatabaseClient db, TransactionalOperator tx) {
        this.db = db;
        this.tx = tx;
    }

    @Override
    public Mono<Purged> purge(String dfId, Instant before, int limit) {
        return db.sql("""
                        select cns_req_id from cns_qry.cns_art_rd_mdl
                        where df_id = :dfId and updated_at < :before and lcy_sts <> 'ISSUED'
                        order by cns_req_id limit :limit
                        """)
                .bind("dfId", dfId).bind("before", before).bind("limit", limit)
                .map((row, meta) -> row.get("cns_req_id", String.class)).all().collectList()
                .flatMap(ids -> (ids.isEmpty() ? Mono.<Void>empty() : tx.transactional(
                        db.sql("delete from cns_qry.cns_hst_rd_mdl where cns_req_id = any(:ids)").bind("ids", ids.toArray(String[]::new)).fetch().rowsUpdated()
                                .then(db.sql("delete from cns_qry.cns_art_rd_mdl where cns_req_id = any(:ids)").bind("ids", ids.toArray(String[]::new)).fetch().rowsUpdated())
                                .then()))
                        .then(Mono.defer(() -> db.sql("select count(*) as n from cns_qry.cns_art_rd_mdl where df_id = :dfId")
                                .bind("dfId", dfId).map((row, meta) -> row.get("n", Long.class)).one()))
                        .map(left -> new Purged(ids.size(), left)));
    }
}
