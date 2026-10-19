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

import java.time.Instant;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubFactory;
import com.techeazyhq.cm.bil.dom.DfSubRepoPort;
import com.techeazyhq.cm.bil.dom.DfSubSts;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcDfSubRepoAdapter implements DfSubRepoPort {

    private final DatabaseClient db;

    public R2dbcDfSubRepoAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<DfSub> save(DfSub dfSub) {
        return db.sql("""
                        insert into bil.df_sub (df_id, sub_pln_id, sts, cur_period_from, cur_period_to)
                        values (:dfId, :subPlnId, :sts, :curPeriodFrom, :curPeriodTo)
                        on conflict (df_id) do update set
                            sub_pln_id = excluded.sub_pln_id,
                            sts = excluded.sts,
                            cur_period_to = excluded.cur_period_to
                        """)
                .bind("dfId", dfSub.id())
                .bind("subPlnId", dfSub.subPlnId())
                .bind("sts", dfSub.sts().name())
                .bind("curPeriodFrom", dfSub.curPeriodFrom())
                .bind("curPeriodTo", dfSub.curPeriodTo())
                .then()
                .thenReturn(dfSub);
    }

    @Override
    public Mono<DfSub> findByDfId(String dfId) {
        return db.sql("select * from bil.df_sub where df_id = :dfId")
                .bind("dfId", dfId)
                .map(R2dbcDfSubRepoAdapter::row)
                .one();
    }

    @Override
    public Flux<DfSub> findAll() {
        return db.sql("select * from bil.df_sub").map(R2dbcDfSubRepoAdapter::row).all();
    }

    private static DfSub row(Readable row) {
        return DfSubFactory.rehydrate(
                row.get("df_id", String.class),
                row.get("sub_pln_id", String.class),
                DfSubSts.valueOf(row.get("sts", String.class)),
                row.get("cur_period_from", Instant.class),
                row.get("cur_period_to", Instant.class));
    }
}
