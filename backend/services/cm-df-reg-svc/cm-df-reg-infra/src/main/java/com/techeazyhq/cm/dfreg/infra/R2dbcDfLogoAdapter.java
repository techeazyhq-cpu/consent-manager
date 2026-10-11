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

import java.nio.ByteBuffer;
import java.time.Instant;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.dfreg.dom.DfLogo;
import com.techeazyhq.cm.dfreg.dom.DfLogoRepoPort;

import reactor.core.publisher.Mono;

/** {@link DfLogoRepoPort} on {@code df_reg.df_logo}: one row per fiduciary, the image as {@code bytea}. */
public class R2dbcDfLogoAdapter implements DfLogoRepoPort {

    private final DatabaseClient db;

    public R2dbcDfLogoAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<DfLogo> save(DfLogo logo) {
        return db.sql("""
                        insert into df_reg.df_logo (df_id, content_type, bytes, updated_at)
                        values (:dfId, :contentType, :bytes, :updatedAt)
                        on conflict (df_id) do update set
                            content_type = excluded.content_type,
                            bytes = excluded.bytes,
                            updated_at = excluded.updated_at
                        """)
                .bind("dfId", logo.dfId())
                .bind("contentType", logo.contentType())
                .bind("bytes", ByteBuffer.wrap(logo.bytes()))
                .bind("updatedAt", logo.updatedAt())
                .then()
                .thenReturn(logo);
    }

    @Override
    public Mono<DfLogo> findByDfId(String dfId) {
        return db.sql("select df_id, content_type, bytes, updated_at from df_reg.df_logo where df_id = :dfId")
                .bind("dfId", dfId)
                .map((row, meta) -> {
                    ByteBuffer buffer = row.get("bytes", ByteBuffer.class);
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    return new DfLogo(row.get("df_id", String.class), row.get("content_type", String.class), bytes,
                            row.get("updated_at", Instant.class));
                })
                .one();
    }
}
