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


package com.techeazyhq.cm.ntf.infra;

import java.time.Instant;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.ntf.dom.DpPref;
import com.techeazyhq.cm.ntf.dom.DpPrefRepoPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Mono;

/** {@link DpPrefRepoPort} on {@code ntf.dp_pref} (ADR-066). */
public class R2dbcDpPrefAdapter implements DpPrefRepoPort {

    private final DatabaseClient db;

    public R2dbcDpPrefAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<DpPref> find(String dpId) {
        return db.sql("select * from ntf.dp_pref where dp_id = :dpId").bind("dpId", dpId).map(R2dbcDpPrefAdapter::row).one();
    }

    @Override
    public Mono<DpPref> findByEmailHash(String emailHash) {
        return db.sql("select * from ntf.dp_pref where email_hash = :hash limit 1").bind("hash", emailHash).map(R2dbcDpPrefAdapter::row).one();
    }

    @Override
    public Mono<DpPref> save(DpPref p) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into ntf.dp_pref (dp_id, push_enabled, email_requests, email_hash, updated_at)
                        values (:dpId, :push, :email, :hash, :at)
                        on conflict (dp_id) do update set push_enabled = excluded.push_enabled,
                            email_requests = excluded.email_requests, email_hash = excluded.email_hash, updated_at = excluded.updated_at
                        """)
                .bind("dpId", p.dpId()).bind("push", p.pushEnabled()).bind("email", p.emailRequests()).bind("at", p.updatedAt());
        spec = p.emailHash() == null ? spec.bindNull("hash", String.class) : spec.bind("hash", p.emailHash());
        return spec.then().thenReturn(p);
    }

    @Override
    public Mono<Long> deleteOf(String dpId) {
        return db.sql("delete from ntf.dp_pref where dp_id = :dpId").bind("dpId", dpId).fetch().rowsUpdated();
    }

    private static DpPref row(Readable r) {
        return new DpPref(r.get("dp_id", String.class), Boolean.TRUE.equals(r.get("push_enabled", Boolean.class)),
                Boolean.TRUE.equals(r.get("email_requests", Boolean.class)), r.get("email_hash", String.class), r.get("updated_at", Instant.class));
    }
}
