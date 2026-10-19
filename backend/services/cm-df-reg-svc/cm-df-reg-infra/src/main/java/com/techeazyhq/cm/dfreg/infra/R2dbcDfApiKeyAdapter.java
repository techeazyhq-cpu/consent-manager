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

import com.techeazyhq.cm.dfreg.dom.DfApiKey;
import com.techeazyhq.cm.dfreg.dom.DfApiKeyRepoPort;
import com.techeazyhq.cm.dfreg.dom.RequestNoncePort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** API keys and their request nonces in df-reg's own schema (ADR-041). */
public class R2dbcDfApiKeyAdapter implements DfApiKeyRepoPort, RequestNoncePort {

    private final DatabaseClient db;

    public R2dbcDfApiKeyAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<DfApiKey> save(DfApiKey key) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into df_reg.api_key (id, df_id, label, key_prefix, key_hash, signing_secret_sealed,
                            signing_required, created_at, created_by, last_used_at, revoked_at)
                        values (:id, :dfId, :label, :prefix, :hash, :sealed, :required, :createdAt, :createdBy, null, :revokedAt)
                        on conflict (id) do update set
                            signing_required = excluded.signing_required,
                            revoked_at = excluded.revoked_at
                        """)
                .bind("id", key.id())
                .bind("dfId", key.dfId())
                .bind("label", key.label())
                .bind("prefix", key.keyPrefix())
                .bind("hash", key.keyHash())
                .bind("sealed", key.sealedSigningSecret())
                .bind("required", key.signingRequired())
                .bind("createdAt", key.createdAt());
        spec = key.createdBy() == null ? spec.bindNull("createdBy", String.class) : spec.bind("createdBy", key.createdBy());
        spec = key.revokedAt() == null ? spec.bindNull("revokedAt", Instant.class) : spec.bind("revokedAt", key.revokedAt());
        return spec.then().thenReturn(key);
    }

    @Override
    public Mono<DfApiKey> findById(String id) {
        return db.sql("select * from df_reg.api_key where id = :id").bind("id", id).map(R2dbcDfApiKeyAdapter::row).one();
    }

    @Override
    public Mono<DfApiKey> findByHash(String keyHash) {
        return db.sql("select * from df_reg.api_key where key_hash = :hash").bind("hash", keyHash)
                .map(R2dbcDfApiKeyAdapter::row).one();
    }

    @Override
    public Flux<DfApiKey> findByDfId(String dfId) {
        return db.sql("select * from df_reg.api_key where df_id = :dfId order by created_at desc").bind("dfId", dfId)
                .map(R2dbcDfApiKeyAdapter::row).all();
    }

    @Override
    public Mono<Void> touch(String id, Instant at) {
        return db.sql("""
                        update df_reg.api_key set last_used_at = :at
                        where id = :id and (last_used_at is null or last_used_at < :at - interval '1 minute')
                        """)
                .bind("id", id).bind("at", at).then();
    }

    /** One statement: takes a free nonce, or one whose claim has expired, and reports whether it did. */
    @Override
    public Mono<Boolean> claim(String keyId, String nonce, Instant expiresAt) {
        return db.sql("""
                        insert into df_reg.api_request_nonce (key_id, nonce, expires_at) values (:keyId, :nonce, :expiresAt)
                        on conflict (key_id, nonce) do update set expires_at = excluded.expires_at
                            where df_reg.api_request_nonce.expires_at < now()
                        returning key_id
                        """)
                .bind("keyId", keyId).bind("nonce", nonce).bind("expiresAt", expiresAt)
                .map((row, meta) -> true)
                .one()
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Long> purgeExpired(Instant now) {
        return db.sql("delete from df_reg.api_request_nonce where expires_at < :now").bind("now", now)
                .fetch().rowsUpdated();
    }

    private static DfApiKey row(Readable r) {
        return DfApiKey.rehydrate(r.get("id", String.class), r.get("df_id", String.class), r.get("label", String.class),
                r.get("key_prefix", String.class), r.get("key_hash", String.class),
                r.get("signing_secret_sealed", String.class), Boolean.TRUE.equals(r.get("signing_required", Boolean.class)),
                r.get("created_at", Instant.class), r.get("created_by", String.class),
                r.get("last_used_at", Instant.class), r.get("revoked_at", Instant.class));
    }
}
