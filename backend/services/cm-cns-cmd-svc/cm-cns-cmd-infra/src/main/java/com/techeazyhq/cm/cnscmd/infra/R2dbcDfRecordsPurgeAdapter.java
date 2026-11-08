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
import org.springframework.transaction.reactive.TransactionalOperator;

import com.techeazyhq.cm.cnscmd.dom.DfRecordsPurgePort;

import reactor.core.publisher.Mono;

/**
 * {@link DfRecordsPurgePort} in SQL. A consent record is one request with everything hanging off it. Its last event is
 * the latest in its record's history, or its creation when it has none; one still in force is never touched. The record's
 * events and receipts are append-only (a trigger refuses any change), and a purge is the one deletion allowed: it says so
 * for its own transaction with {@code cm.retention_purge}, and anything else still fails.
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
                        select r.id from cns_cmd.cns_req r
                        where r.df_id = :dfId
                          and coalesce((select max(e.occurred_at) from cns_cmd.cns_rec_evt e
                                        join cns_cmd.cns_rec c on c.id = e.rec_id where c.cns_req_id = r.id), r.created_at) < :before
                          and not exists (select 1 from cns_cmd.cns_art a where a.cns_req_id = r.id and a.sts = 'ISSUED')
                        order by r.id limit :limit
                        """)
                .bind("dfId", dfId).bind("before", before).bind("limit", limit)
                .map((row, meta) -> row.get("id", String.class)).all().collectList()
                .flatMap(ids -> ids.isEmpty() ? remaining(dfId).flatMap(left -> leftovers(dfId, left).thenReturn(new Purged(0, left)))
                        : delete(ids).then(Mono.defer(() -> remaining(dfId))).flatMap(left -> leftovers(dfId, left)
                                .thenReturn(new Purged(ids.size(), left))));
    }

    private Mono<Long> remaining(String dfId) {
        return db.sql("select count(*) as n from cns_cmd.cns_req where df_id = :dfId").bind("dfId", dfId)
                .map((row, meta) -> row.get("n", Long.class)).one();
    }

    private Mono<Void> delete(java.util.List<String> ids) {
        String[] reqs = ids.toArray(String[]::new);
        String rec = "(select id from cns_cmd.cns_rec where cns_req_id = any(:ids))";
        String art = "(select id from cns_cmd.cns_art where cns_req_id = any(:ids))";
        return tx.transactional(db.sql("select set_config('cm.retention_purge', 'on', true)").fetch().rowsUpdated()
                .then(run("delete from cns_cmd.cns_rcpt_pending where rec_id in " + rec, reqs))
                .then(run("delete from cns_cmd.cns_rcpt where rec_id in " + rec, reqs))
                .then(run("delete from cns_cmd.cns_rec_evt where rec_id in " + rec, reqs))
                .then(run("delete from cns_cmd.cns_rec where cns_req_id = any(:ids)", reqs))
                .then(run("delete from cns_cmd.outbox_evt where published_at is not null and (agg_id = any(:ids) or agg_id in " + art + ")", reqs))
                .then(run("delete from cns_cmd.sga_state where cns_id = any(:ids) or cns_id in " + art, reqs))
                .then(run("delete from cns_cmd.cns_req_batch_row where cns_req_id = any(:ids)", reqs))
                .then(run("delete from cns_cmd.cns_art where cns_req_id = any(:ids)", reqs))
                .then(run("delete from cns_cmd.cns_req where id = any(:ids)", reqs))
                .then());
    }

    /** Once no request is left, what only they used: the fiduciary's purposes, webhook endpoints and batches. */
    private Mono<Void> leftovers(String dfId, long left) {
        if (left > 0) {
            return Mono.empty();
        }
        return tx.transactional(db.sql("select set_config('cm.retention_purge', 'on', true)").fetch().rowsUpdated()
                .then(db.sql("delete from cns_cmd.cns_req_batch where df_id = :dfId").bind("dfId", dfId).fetch().rowsUpdated())
                .then(db.sql("delete from cns_cmd.whk_delivery where df_id = :dfId").bind("dfId", dfId).fetch().rowsUpdated())
                .then(db.sql("delete from cns_cmd.whk_endpoint where df_id = :dfId").bind("dfId", dfId).fetch().rowsUpdated())
                .then(db.sql("delete from cns_cmd.prps_ver where prps_def_id in (select id from cns_cmd.prps_def where df_id = :dfId)")
                        .bind("dfId", dfId).fetch().rowsUpdated())
                .then(db.sql("delete from cns_cmd.prps_def where df_id = :dfId").bind("dfId", dfId).fetch().rowsUpdated())
                .then());
    }

    private Mono<Long> run(String sql, String[] ids) {
        return db.sql(sql).bind("ids", ids).fetch().rowsUpdated();
    }
}
