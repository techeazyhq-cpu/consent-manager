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

import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfig;
import com.techeazyhq.cm.cnscmd.dom.AiDraftingConfigRepoPort;
import com.techeazyhq.cm.cnscmd.dom.AiProvider;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Mono;

/** {@link AiDraftingConfigRepoPort} adapter (ADR-113): one row, id 1. */
public class R2dbcAiDraftingConfigAdapter implements AiDraftingConfigRepoPort {

    private final DatabaseClient db;

    public R2dbcAiDraftingConfigAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<AiDraftingConfig> find() {
        return db.sql("select * from cns_cmd.ai_drafting_config where id = 1").map((row, meta) -> config(row)).one();
    }

    @Override
    public Mono<AiDraftingConfig> save(AiDraftingConfig c) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.ai_drafting_config (id, enabled, provider, endpoint, model, api_key_sealed, timeout_seconds, max_tokens,
                                                                third_party_ack, updated_by, updated_at)
                        values (1, :enabled, :provider, :endpoint, :model, :key, :timeout, :maxTokens, :ack, :by, :at)
                        on conflict (id) do update set
                            enabled = excluded.enabled, provider = excluded.provider, endpoint = excluded.endpoint, model = excluded.model,
                            api_key_sealed = excluded.api_key_sealed, timeout_seconds = excluded.timeout_seconds, max_tokens = excluded.max_tokens,
                            third_party_ack = excluded.third_party_ack, updated_by = excluded.updated_by, updated_at = excluded.updated_at
                        """)
                .bind("enabled", c.enabled())
                .bind("provider", c.provider().name())
                .bind("endpoint", c.endpoint() == null ? "" : c.endpoint())
                .bind("model", c.model())
                .bind("timeout", c.timeoutSeconds())
                .bind("maxTokens", c.maxTokens())
                .bind("ack", c.thirdPartyAck())
                .bind("by", c.updatedBy() == null ? "" : c.updatedBy())
                .bind("at", c.updatedAt());
        spec = R2dbcBinds.bindNullable(spec, "key", c.apiKeySealed(), String.class);
        return spec.then().thenReturn(c);
    }

    private static AiDraftingConfig config(Readable row) {
        return new AiDraftingConfig(Boolean.TRUE.equals(row.get("enabled", Boolean.class)), AiProvider.valueOf(row.get("provider", String.class)),
                row.get("endpoint", String.class), row.get("model", String.class), row.get("api_key_sealed", String.class),
                row.get("timeout_seconds", Integer.class), row.get("max_tokens", Integer.class),
                Boolean.TRUE.equals(row.get("third_party_ack", Boolean.class)), row.get("updated_by", String.class), row.get("updated_at", Instant.class));
    }
}
