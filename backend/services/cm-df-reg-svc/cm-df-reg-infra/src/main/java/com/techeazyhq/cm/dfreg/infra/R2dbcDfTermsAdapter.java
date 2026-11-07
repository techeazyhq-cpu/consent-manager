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

import com.techeazyhq.cm.dfreg.dom.DfTermsAcceptance;
import com.techeazyhq.cm.dfreg.dom.DfTermsRepoPort;
import com.techeazyhq.cm.dfreg.dom.TermsDoc;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Acceptances of the consent manager's terms and data processing agreement, in df-reg's own schema (ADR-063). */
public class R2dbcDfTermsAdapter implements DfTermsRepoPort {

    private final DatabaseClient db;

    public R2dbcDfTermsAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<DfTermsAcceptance> saveIfAbsent(DfTermsAcceptance a) {
        return db.sql("""
                        insert into df_reg.df_terms_acceptance (id, df_id, doc, version, accepted_by, accepted_at)
                        values (:id, :dfId, :doc, :version, :by, :at)
                        on conflict (df_id, doc, version) do nothing
                        """)
                .bind("id", a.id()).bind("dfId", a.dfId()).bind("doc", a.doc().name()).bind("version", a.version())
                .bind("by", a.acceptedBy()).bind("at", a.acceptedAt())
                .then()
                .then(Mono.defer(() -> db.sql("select * from df_reg.df_terms_acceptance where df_id = :dfId and doc = :doc and version = :version")
                        .bind("dfId", a.dfId()).bind("doc", a.doc().name()).bind("version", a.version())
                        .map(R2dbcDfTermsAdapter::row).one()));
    }

    @Override
    public Flux<DfTermsAcceptance> findByDfId(String dfId) {
        return db.sql("select * from df_reg.df_terms_acceptance where df_id = :dfId order by accepted_at desc")
                .bind("dfId", dfId).map(R2dbcDfTermsAdapter::row).all();
    }

    private static DfTermsAcceptance row(Readable r) {
        return new DfTermsAcceptance(r.get("id", String.class), r.get("df_id", String.class),
                TermsDoc.valueOf(r.get("doc", String.class)), r.get("version", String.class),
                r.get("accepted_by", String.class), r.get("accepted_at", Instant.class));
    }
}
