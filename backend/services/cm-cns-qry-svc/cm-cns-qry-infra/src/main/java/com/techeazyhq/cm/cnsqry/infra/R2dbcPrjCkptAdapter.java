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

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.cnsqry.dom.PrjCkptPort;

import reactor.core.publisher.Mono;

public class R2dbcPrjCkptAdapter implements PrjCkptPort {

    private final DatabaseClient db;

    public R2dbcPrjCkptAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Boolean> alreadyProcessed(String evtId) {
        // Missing .bind("evtId", evtId) here silently broke every single projected event —
        // the query's :evtId placeholder was never filled, and R2DBC throws rather than
        // running with an unbound parameter, so the whole Projector chain errored out before
        // ever reaching the upsert. Found during Phase 1 verification.
        return db.sql("select 1 from cns_qry.prj_ckpt where evt_id = :evtId")
                .bind("evtId", evtId)
                .map((row, meta) -> true)
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Void> markProcessed(String evtId) {
        return db.sql("insert into cns_qry.prj_ckpt (evt_id, processed_at) values (:evtId, now()) on conflict (evt_id) do nothing")
                .bind("evtId", evtId)
                .then();
    }
}
