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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.PrpsChange;
import com.techeazyhq.cm.cnscmd.dom.PrpsDCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsDef;
import com.techeazyhq.cm.cnscmd.dom.PrpsDefRepoPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsFlag;
import com.techeazyhq.cm.cnscmd.dom.PrpsNtc;
import com.techeazyhq.cm.cnscmd.dom.PrpsSensitivity;
import com.techeazyhq.cm.cnscmd.dom.PrpsVer;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link PrpsDefRepoPort} adapter (ADR-031). A version's content is one JSON document, as published; a published row
 * is only ever inserted, and the table's trigger refuses any change to it.
 */
public class R2dbcPrpsDefRepoAdapter implements PrpsDefRepoPort {

    private static final String SELECT = """
            select d.id as def_id, d.df_id, d.code, d.created_at as def_created_at,
                   v.id as ver_id, v.ver_no, v.sts, v.content, v.change, v.created_at as ver_created_at, v.published_at
            from cns_cmd.prps_def d
            left join cns_cmd.prps_ver v on v.prps_def_id = d.id
            """;

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;
    private final ObxWriter obxWriter;

    public R2dbcPrpsDefRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
        this.obxWriter = new ObxWriter(db, objectMapper);
    }

    @Override
    public Mono<PrpsDef> save(PrpsDef def) {
        Mono<Void> upsertDef = db.sql("""
                        insert into cns_cmd.prps_def (id, df_id, code, created_at)
                        values (:id, :dfId, :code, :createdAt)
                        on conflict (id) do nothing
                        """)
                .bind("id", def.id())
                .bind("dfId", def.dfId())
                .bind("code", def.code())
                .bind("createdAt", def.createdAt())
                .then();

        // a discarded draft, or one replaced by publishing, goes; published rows are never touched
        String keptDraft = def.draft().map(PrpsVer::id).orElse("");
        Mono<Void> dropOtherDrafts = db.sql("""
                        delete from cns_cmd.prps_ver
                        where prps_def_id = :defId and sts = 'DRAFT' and id <> :keep
                        """)
                .bind("defId", def.id())
                .bind("keep", keptDraft)
                .then();

        Mono<Void> upsertVersions = Flux.fromIterable(def.versions())
                .concatMap(ver -> upsertVersion(def.id(), ver))
                .then();

        Mono<Void> drainOutbox = Flux.defer(() -> Flux.fromIterable(def.pullEvts()))
                .concatMap(evt -> obxWriter.insert(evt, "PrpsDef", def.id()))
                .then();

        return upsertDef.then(dropOtherDrafts).then(upsertVersions).then(drainOutbox).thenReturn(def);
    }

    /** Inserts a new version; updates it only while it is a draft, including the update that publishes it. */
    private Mono<Void> upsertVersion(String defId, PrpsVer ver) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.prps_ver
                            (id, prps_def_id, ver_no, sts, content, change, created_at, published_at)
                        values
                            (:id, :defId, :verNo, :sts, cast(:content as jsonb), cast(:change as jsonb), :createdAt,
                             :publishedAt)
                        on conflict (id) do update set
                            sts = excluded.sts,
                            content = excluded.content,
                            change = excluded.change,
                            published_at = excluded.published_at
                        where cns_cmd.prps_ver.sts = 'DRAFT'
                        """)
                .bind("id", ver.id())
                .bind("defId", defId)
                .bind("verNo", ver.verNo())
                .bind("sts", ver.sts().name())
                .bind("content", json(ContentJson.of(ver.content())))
                .bind("createdAt", ver.createdAt());
        spec = R2dbcBinds.bindNullable(spec, "change", ver.change() == null ? null : json(ChangeJson.of(ver.change())),
                String.class);
        spec = R2dbcBinds.bindNullable(spec, "publishedAt", ver.publishedAt(), Instant.class);
        return spec.then();
    }

    @Override
    public Mono<PrpsDef> findByCode(String dfId, String code) {
        return db.sql(SELECT + " where d.df_id = :dfId and d.code = :code order by v.ver_no")
                .bind("dfId", dfId)
                .bind("code", code)
                .map((row, meta) -> new Joined(row, this))
                .all()
                .collectList()
                .flatMapMany(rows -> Flux.fromIterable(assemble(rows)))
                .next();
    }

    @Override
    public Flux<PrpsDef> listByDf(String dfId) {
        return db.sql(SELECT + " where d.df_id = :dfId order by d.code, v.ver_no")
                .bind("dfId", dfId)
                .map((row, meta) -> new Joined(row, this))
                .all()
                .collectList()
                .flatMapMany(rows -> Flux.fromIterable(assemble(rows)));
    }

    @Override
    public Mono<PrpsVer> findVersion(String prpsVerId) {
        return db.sql(SELECT + " where v.id = :id")
                .bind("id", prpsVerId)
                .map((row, meta) -> new Joined(row, this))
                .one()
                .mapNotNull(joined -> joined.ver);
    }

    private static List<PrpsDef> assemble(List<Joined> rows) {
        Map<String, Joined> defs = new LinkedHashMap<>();
        Map<String, List<PrpsVer>> versions = new LinkedHashMap<>();
        for (Joined row : rows) {
            defs.putIfAbsent(row.defId, row);
            List<PrpsVer> list = versions.computeIfAbsent(row.defId, id -> new ArrayList<>());
            if (row.ver != null) {
                list.add(row.ver);
            }
        }
        return defs.values().stream()
                .map(d -> PrpsDef.rehydrate(d.defId, d.dfId, d.code, d.createdAt, versions.get(d.defId)))
                .toList();
    }

    /** One row of the purpose-and-version join, read while the row is open. */
    private static final class Joined {
        final String defId;
        final String dfId;
        final String code;
        final Instant createdAt;
        final PrpsVer ver;

        Joined(Readable row, R2dbcPrpsDefRepoAdapter adapter) {
            defId = row.get("def_id", String.class);
            dfId = row.get("df_id", String.class);
            code = row.get("code", String.class);
            createdAt = row.get("def_created_at", Instant.class);
            String verId = row.get("ver_id", String.class);
            if (verId == null) {
                ver = null;
                return;
            }
            String change = row.get("change", String.class);
            ver = new PrpsVer(verId, row.get("ver_no", Integer.class), PrpsVer.Sts.valueOf(row.get("sts", String.class)),
                    adapter.read(row.get("content", String.class), ContentJson.class).toDomain(),
                    change == null ? null : adapter.read(change, ChangeJson.class).toDomain(),
                    row.get("ver_created_at", Instant.class), row.get("published_at", Instant.class));
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize a purpose version", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read a stored purpose version", e);
        }
    }

    // ---- the stored JSON, kept apart from the domain so either can change without the other ----

    record DCatJson(String code, String dpv, String sensitivity, boolean necessary, String consequence) {
    }

    record NtcJson(String lang, String title, String text) {
    }

    record ContentJson(String name, String description, List<DCatJson> dCats, List<String> recipients,
                       List<String> transfers, int retentionDays, int requestedValidityDays, int maxValidityDays,
                       List<String> flags, List<NtcJson> notices) {

        static ContentJson of(PrpsVerContent c) {
            return new ContentJson(c.name(), c.description(),
                    c.dCats().stream().map(d -> new DCatJson(d.code(), d.dpv(), d.sensitivity().name(), d.necessary(),
                            d.consequence())).toList(),
                    c.recipients(), c.transfers(), c.retentionDays(), c.requestedValidityDays(), c.maxValidityDays(),
                    c.flags().stream().map(Enum::name).sorted().toList(),
                    c.notices().stream().map(n -> new NtcJson(n.lang(), n.title(), n.text())).toList());
        }

        PrpsVerContent toDomain() {
            return new PrpsVerContent(name, description,
                    dCats == null ? List.of() : dCats.stream().map(d -> new PrpsDCat(d.code(), d.dpv(),
                            d.sensitivity() == null ? null : PrpsSensitivity.valueOf(d.sensitivity()), d.necessary(),
                            d.consequence())).toList(),
                    recipients, transfers, retentionDays, requestedValidityDays, maxValidityDays,
                    flags == null ? Set.of() : Set.copyOf(flags.stream().map(PrpsFlag::valueOf).toList()),
                    notices == null ? List.of()
                            : notices.stream().map(n -> new PrpsNtc(n.lang(), n.title(), n.text())).toList());
        }
    }

    record ChangeJson(String kind, List<String> material, List<String> minor) {

        static ChangeJson of(PrpsChange c) {
            return new ChangeJson(c.kind().name(), c.material(), c.minor());
        }

        PrpsChange toDomain() {
            return new PrpsChange(PrpsChange.Kind.valueOf(kind), material == null ? List.of() : material,
                    minor == null ? List.of() : minor);
        }
    }
}
