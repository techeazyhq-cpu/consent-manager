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

import com.techeazyhq.cm.dfreg.dom.DfSignup;
import com.techeazyhq.cm.dfreg.dom.DfSignupRepoPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Mono;

/** Applications on the public sign-up page, in df-reg's own schema (ADR-065). */
public class R2dbcDfSignupAdapter implements DfSignupRepoPort {

    private final DatabaseClient db;

    public R2dbcDfSignupAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<DfSignup> save(DfSignup s) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into df_reg.df_signup (id, name, contact_email, legal_name, industry, website, token_hash, created_at,
                                                      expires_at, status, verified_at, df_id)
                        values (:id, :name, :email, :legalName, :industry, :website, :hash, :createdAt, :expiresAt, :status, :verifiedAt, :dfId)
                        on conflict (id) do update set status = excluded.status, verified_at = excluded.verified_at, df_id = excluded.df_id
                        """)
                .bind("id", s.id()).bind("name", s.name()).bind("email", s.contactEmail()).bind("hash", s.tokenHash())
                .bind("createdAt", s.createdAt()).bind("expiresAt", s.expiresAt()).bind("status", s.status().name());
        spec = s.legalName() == null ? spec.bindNull("legalName", String.class) : spec.bind("legalName", s.legalName());
        spec = s.industry() == null ? spec.bindNull("industry", String.class) : spec.bind("industry", s.industry());
        spec = s.website() == null ? spec.bindNull("website", String.class) : spec.bind("website", s.website());
        spec = s.verifiedAt() == null ? spec.bindNull("verifiedAt", Instant.class) : spec.bind("verifiedAt", s.verifiedAt());
        spec = s.dfId() == null ? spec.bindNull("dfId", String.class) : spec.bind("dfId", s.dfId());
        return spec.then().thenReturn(s);
    }

    @Override
    public Mono<DfSignup> findById(String id) {
        return db.sql("select * from df_reg.df_signup where id = :id").bind("id", id).map(R2dbcDfSignupAdapter::row).one();
    }

    @Override
    public Mono<Long> countByEmailSince(String contactEmail, Instant since) {
        return db.sql("select count(*) as n from df_reg.df_signup where contact_email = :email and created_at >= :since")
                .bind("email", contactEmail).bind("since", since).map((r, m) -> r.get("n", Long.class)).one();
    }

    @Override
    public Mono<Long> countSince(Instant since) {
        return db.sql("select count(*) as n from df_reg.df_signup where created_at >= :since")
                .bind("since", since).map((r, m) -> r.get("n", Long.class)).one();
    }

    @Override
    public Mono<Long> deleteUnconfirmedExpiredBefore(Instant before) {
        return db.sql("delete from df_reg.df_signup where status = 'AWAITING_EMAIL' and expires_at < :before")
                .bind("before", before).fetch().rowsUpdated();
    }

    private static DfSignup row(Readable r) {
        return DfSignup.rehydrate(r.get("id", String.class), r.get("name", String.class), r.get("contact_email", String.class),
                r.get("legal_name", String.class), r.get("industry", String.class), r.get("website", String.class),
                r.get("token_hash", String.class), r.get("created_at", Instant.class), r.get("expires_at", Instant.class),
                DfSignup.Status.valueOf(r.get("status", String.class)), r.get("verified_at", Instant.class), r.get("df_id", String.class));
    }
}
