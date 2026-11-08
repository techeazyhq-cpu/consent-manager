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

import com.techeazyhq.cm.cnscmd.dom.Processor;
import com.techeazyhq.cm.cnscmd.dom.ProcessorRepoPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link ProcessorRepoPort} adapter (ADR-075). */
public class R2dbcProcessorRepoAdapter implements ProcessorRepoPort {

    private final DatabaseClient db;

    public R2dbcProcessorRepoAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Processor> save(Processor p) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.processor (id, df_id, name, role, country, contact_email, sts, created_at, retired_at)
                        values (:id, :dfId, :name, :role, :country, :contact, :sts, :createdAt, :retiredAt)
                        on conflict (id) do update set
                            role = excluded.role,
                            country = excluded.country,
                            contact_email = excluded.contact_email,
                            sts = excluded.sts,
                            retired_at = excluded.retired_at
                        """)
                .bind("id", p.id())
                .bind("dfId", p.dfId())
                .bind("name", p.name())
                .bind("role", p.role())
                .bind("country", p.country())
                .bind("contact", p.contactEmail())
                .bind("sts", p.sts().name())
                .bind("createdAt", p.createdAt());
        spec = R2dbcBinds.bindNullable(spec, "retiredAt", p.retiredAt(), Instant.class);
        return spec.then().thenReturn(p);
    }

    @Override
    public Mono<Processor> find(String dfId, String id) {
        return db.sql("select * from cns_cmd.processor where df_id = :dfId and id = :id")
                .bind("dfId", dfId).bind("id", id)
                .map((row, meta) -> processor(row)).one();
    }

    @Override
    public Mono<Processor> findByName(String dfId, String name) {
        return db.sql("select * from cns_cmd.processor where df_id = :dfId and lower(name) = :key")
                .bind("dfId", dfId).bind("key", Processor.key(name))
                .map((row, meta) -> processor(row)).one();
    }

    @Override
    public Flux<Processor> list(String dfId) {
        return db.sql("select * from cns_cmd.processor where df_id = :dfId order by lower(name)")
                .bind("dfId", dfId)
                .map((row, meta) -> processor(row)).all();
    }

    private static Processor processor(Readable row) {
        return new Processor(row.get("id", String.class), row.get("df_id", String.class), row.get("name", String.class),
                row.get("role", String.class), row.get("country", String.class), row.get("contact_email", String.class),
                Processor.Status.valueOf(row.get("sts", String.class)), row.get("created_at", Instant.class),
                row.get("retired_at", Instant.class));
    }
}
