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

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.bil.dom.SubPln;
import com.techeazyhq.cm.bil.dom.SubPlnRepoPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcSubPlnRepoAdapter implements SubPlnRepoPort {

    private final DatabaseClient db;

    public R2dbcSubPlnRepoAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<SubPln> save(SubPln subPln) {
        return db.sql("""
                        insert into bil.sub_pln (id, name, req_per_sec_qta, req_per_month_qta)
                        values (:id, :name, :reqPerSecQta, :reqPerMonthQta)
                        on conflict (id) do update set
                            name = excluded.name, req_per_sec_qta = excluded.req_per_sec_qta, req_per_month_qta = excluded.req_per_month_qta
                        """)
                .bind("id", subPln.id())
                .bind("name", subPln.name())
                .bind("reqPerSecQta", subPln.reqPerSecQta())
                .bind("reqPerMonthQta", subPln.reqPerMonthQta())
                .then()
                .thenReturn(subPln);
    }

    @Override
    public Mono<SubPln> findById(String id) {
        return db.sql("select * from bil.sub_pln where id = :id").bind("id", id).map(this::toDomain).one();
    }

    @Override
    public Mono<SubPln> findByName(String name) {
        return db.sql("select * from bil.sub_pln where name = :name").bind("name", name).map(this::toDomain).one();
    }

    @Override
    public Flux<SubPln> findAll() {
        return db.sql("select * from bil.sub_pln order by req_per_month_qta").map(this::toDomain).all();
    }

    private SubPln toDomain(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        return new SubPln(row.get("id", String.class), row.get("name", String.class),
                row.get("req_per_sec_qta", Long.class), row.get("req_per_month_qta", Long.class));
    }
}
