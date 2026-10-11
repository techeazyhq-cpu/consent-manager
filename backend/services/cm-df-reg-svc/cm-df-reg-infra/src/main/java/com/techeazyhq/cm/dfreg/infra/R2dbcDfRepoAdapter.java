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
import java.util.List;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcDfRepoAdapter implements DfRepoPort {

    private final DatabaseClient db;
    private final ObxWriter obxWriter;

    public R2dbcDfRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.obxWriter = new ObxWriter(db, objectMapper);
    }

    @Override
    public Mono<Df> save(Df df) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into df_reg.df (id, name, contact_email, sts, registered_at, cm_df_api_client_secret, activated_at,
                                               legal_name, contact_phone, website, registered_address, industry, cin,
                                               gstin, dpo_name, dpo_email, sub_pln_name)
                        values (:id, :name, :contactEmail, :sts, :registeredAt, :clientSecret, :activatedAt,
                                :legalName, :contactPhone, :website, :registeredAddress, :industry, :cin,
                                :gstin, :dpoName, :dpoEmail, :subPlnName)
                        on conflict (id) do update set
                            sts = excluded.sts,
                            cm_df_api_client_secret = excluded.cm_df_api_client_secret,
                            activated_at = excluded.activated_at
                        """)
                .bind("id", df.id())
                .bind("name", df.name())
                .bind("contactEmail", df.contactEmail())
                .bind("sts", df.sts().name())
                .bind("registeredAt", df.registeredAt());
        spec = bindNullable(spec, "clientSecret", df.cmDfApiClientSecret());
        spec = bindNullableInstant(spec, "activatedAt", df.activatedAt());
        DfPrfl prfl = df.prfl();
        spec = bindNullable(spec, "legalName", prfl.legalName());
        spec = bindNullable(spec, "contactPhone", prfl.contactPhone());
        spec = bindNullable(spec, "website", prfl.website());
        spec = bindNullable(spec, "registeredAddress", prfl.registeredAddress());
        spec = bindNullable(spec, "industry", prfl.industry());
        spec = bindNullable(spec, "cin", prfl.cin());
        spec = bindNullable(spec, "gstin", prfl.gstin());
        spec = bindNullable(spec, "dpoName", prfl.dpoName());
        spec = bindNullable(spec, "dpoEmail", prfl.dpoEmail());
        spec = bindNullable(spec, "subPlnName", df.subPlnName());

        Mono<Void> upsert = spec.then();
        List<DomainEvt> pending = df.pullEvts();
        Mono<Void> drainOutbox = Flux.fromIterable(pending).concatMap(evt -> obxWriter.insert(evt, "Df", df.id())).then();

        return upsert.then(drainOutbox).thenReturn(df);
    }

    @Override
    public Mono<Df> findById(String id) {
        return db.sql("select * from df_reg.df where id = :id")
                .bind("id", id)
                .map(this::toDomain)
                .one();
    }

    @Override
    public Flux<Df> findAll() {
        return db.sql("select * from df_reg.df order by registered_at desc").map(this::toDomain).all();
    }

    private Df toDomain(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        return DfFactory.rehydrate(
                row.get("id", String.class),
                row.get("name", String.class),
                row.get("contact_email", String.class),
                new DfPrfl(
                        row.get("legal_name", String.class),
                        row.get("contact_phone", String.class),
                        row.get("website", String.class),
                        row.get("registered_address", String.class),
                        row.get("industry", String.class),
                        row.get("cin", String.class),
                        row.get("gstin", String.class),
                        row.get("dpo_name", String.class),
                        row.get("dpo_email", String.class)),
                row.get("sub_pln_name", String.class),
                DfSts.valueOf(row.get("sts", String.class)),
                row.get("registered_at", Instant.class),
                row.get("cm_df_api_client_secret", String.class),
                row.get("activated_at", Instant.class));
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private DatabaseClient.GenericExecuteSpec bindNullableInstant(DatabaseClient.GenericExecuteSpec spec, String name, Instant value) {
        return value == null ? spec.bindNull(name, Instant.class) : spec.bind(name, value);
    }
}
