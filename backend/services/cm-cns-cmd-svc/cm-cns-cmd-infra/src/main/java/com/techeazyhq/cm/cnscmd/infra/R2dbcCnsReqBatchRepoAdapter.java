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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatch;
import com.techeazyhq.cm.cnscmd.dom.CnsReqBatchRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqTemplate;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.cnscmd.dom.Prps;
import com.techeazyhq.cm.cnscmd.dom.Scp;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link CnsReqBatchRepoPort} on Postgres (ADR-036). A batch's rows go in as one statement, however many. */
public class R2dbcCnsReqBatchRepoAdapter implements CnsReqBatchRepoPort {

    /** A row taken longer ago than this by a worker that never finished it may be taken again. */
    static final String CLAIM_TIMEOUT = "5 minutes";

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;

    public R2dbcCnsReqBatchRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<CnsReqBatch> create(CnsReqBatch batch, List<CnsReqBatch.Row> rows) {
        CnsReqTemplate t = batch.template();
        DatabaseClient.GenericExecuteSpec insertBatch = db.sql("""
                        insert into cns_cmd.cns_req_batch
                            (id, df_id, prps_code, d_cats, ntc_title, ntc_text, source, file_name, created_by, created_at,
                             total, valid, invalid, duplicate, sent, failed, sts, finished_at,
                             prps_ver_id, prps_ver_no, ntc_lang, ntc_hash)
                        values
                            (:id, :dfId, :prpsCode, cast(:dCats as jsonb), :ntcTitle, :ntcText, :source, :fileName,
                             :createdBy, :createdAt, :total, :valid, :invalid, :duplicate, 0, 0, :sts, :finishedAt,
                             :prpsVerId, :prpsVerNo, :ntcLang, :ntcHash)
                        """)
                .bind("id", batch.id())
                .bind("dfId", batch.dfId())
                .bind("prpsCode", t.prps().code())
                .bind("dCats", writeDCats(t))
                .bind("ntcTitle", t.ntcTitle())
                .bind("ntcText", t.ntcText())
                .bind("source", batch.source().name())
                .bind("createdAt", batch.createdAt())
                .bind("total", batch.total())
                .bind("valid", batch.valid())
                .bind("invalid", batch.invalid())
                .bind("duplicate", batch.duplicate())
                .bind("sts", batch.sts().name());
        insertBatch = PrpsVerColumns.bind(insertBatch, t.prpsVer());
        insertBatch = R2dbcBinds.bindNullable(insertBatch, "fileName", batch.fileName(), String.class);
        insertBatch = R2dbcBinds.bindNullable(insertBatch, "createdBy", batch.createdBy(), String.class);
        insertBatch = R2dbcBinds.bindNullable(insertBatch, "finishedAt", batch.finishedAt(), Instant.class);

        int n = rows.size();
        Integer[] rowNos = new Integer[n];
        String[] contacts = new String[n];
        String[] refs = new String[n];
        String[] typs = new String[n];
        String[] vals = new String[n];
        String[] outcomes = new String[n];
        String[] problems = new String[n];
        for (int i = 0; i < n; i++) {
            CnsReqBatch.Row r = rows.get(i);
            rowNos[i] = r.rowNo();
            contacts[i] = r.contact();
            refs[i] = r.ref();
            typs[i] = r.typ() == null ? null : r.typ().name();
            vals[i] = r.val();
            outcomes[i] = r.outcome().name();
            problems[i] = r.problem();
        }
        Mono<Void> insertRows = db.sql("""
                        insert into cns_cmd.cns_req_batch_row (batch_id, row_no, contact, ref, typ, val, outcome, problem)
                        select :batchId, * from unnest(:rowNos::int[], :contacts::varchar[], :refs::varchar[],
                            :typs::varchar[], :vals::varchar[], :outcomes::varchar[], :problems::varchar[])
                        """)
                .bind("batchId", batch.id())
                .bind("rowNos", rowNos)
                .bind("contacts", contacts)
                .bind("refs", refs)
                .bind("typs", typs)
                .bind("vals", vals)
                .bind("outcomes", outcomes)
                .bind("problems", problems)
                .then();
        return insertBatch.then().then(insertRows).thenReturn(batch);
    }

    @Override
    public Mono<CnsReqBatch> save(CnsReqBatch batch) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        update cns_cmd.cns_req_batch
                        set sent = :sent, failed = :failed, sts = :sts, finished_at = :finishedAt
                        where id = :id
                        """)
                .bind("id", batch.id())
                .bind("sent", batch.sent())
                .bind("failed", batch.failed())
                .bind("sts", batch.sts().name());
        spec = R2dbcBinds.bindNullable(spec, "finishedAt", batch.finishedAt(), Instant.class);
        return spec.then().thenReturn(batch);
    }

    @Override
    public Mono<CnsReqBatch> findById(String id) {
        return db.sql("select * from cns_cmd.cns_req_batch where id = :id").bind("id", id).map(this::toBatch).one();
    }

    @Override
    public Flux<CnsReqBatch> findByDfId(String dfId, int limit, int offset) {
        return db.sql("""
                        select * from cns_cmd.cns_req_batch where df_id = :dfId
                        order by created_at desc limit :limit offset :offset
                        """)
                .bind("dfId", dfId).bind("limit", limit).bind("offset", offset)
                .map(this::toBatch).all();
    }

    @Override
    public Flux<CnsReqBatch> findUnfinished(int limit) {
        return db.sql("select * from cns_cmd.cns_req_batch where sts <> 'DONE' order by created_at limit :limit")
                .bind("limit", limit).map(this::toBatch).all();
    }

    @Override
    public Flux<CnsReqBatch.Row> claimPending(String batchId, int limit) {
        return db.sql("""
                        update cns_cmd.cns_req_batch_row set claimed_at = now()
                        where (batch_id, row_no) in (
                            select batch_id, row_no from cns_cmd.cns_req_batch_row
                            where batch_id = :batchId and outcome = 'PENDING'
                              and (claimed_at is null or claimed_at < now() - interval '%s')
                            order by row_no limit :limit
                            for update skip locked)
                        returning *
                        """.formatted(CLAIM_TIMEOUT))
                .bind("batchId", batchId).bind("limit", limit)
                .map(this::toRow).all()
                .sort(Comparator.comparingInt(CnsReqBatch.Row::rowNo));
    }

    @Override
    public Mono<Void> saveRow(String batchId, CnsReqBatch.Row row) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        update cns_cmd.cns_req_batch_row
                        set outcome = :outcome, cns_req_id = :cnsReqId, problem = :problem
                        where batch_id = :batchId and row_no = :rowNo
                        """)
                .bind("batchId", batchId)
                .bind("rowNo", row.rowNo())
                .bind("outcome", row.outcome().name());
        spec = R2dbcBinds.bindNullable(spec, "cnsReqId", row.cnsReqId(), String.class);
        spec = R2dbcBinds.bindNullable(spec, "problem", row.problem() == null ? null
                : row.problem().substring(0, Math.min(255, row.problem().length())), String.class);
        return spec.then();
    }

    @Override
    public Flux<CnsReqBatch.Row> rows(String batchId, CnsReqBatch.Outcome outcome, int limit, int offset) {
        String where = outcome == null ? "" : " and outcome = :outcome";
        DatabaseClient.GenericExecuteSpec spec = db.sql("select * from cns_cmd.cns_req_batch_row where batch_id = :batchId"
                        + where + " order by row_no limit :limit offset :offset")
                .bind("batchId", batchId).bind("limit", limit).bind("offset", offset);
        if (outcome != null) {
            spec = spec.bind("outcome", outcome.name());
        }
        return spec.map(this::toRow).all();
    }

    private CnsReqBatch toBatch(Readable row) {
        CnsReqTemplate template = new CnsReqTemplate(new Prps(row.get("prps_code", String.class)),
                readScp(row.get("d_cats", String.class)), row.get("ntc_title", String.class), row.get("ntc_text", String.class),
                PrpsVerColumns.read(row));
        return CnsReqBatch.rehydrate(
                row.get("id", String.class),
                row.get("df_id", String.class),
                template,
                CnsReqBatch.Source.valueOf(row.get("source", String.class)),
                row.get("file_name", String.class),
                row.get("created_by", String.class),
                row.get("created_at", Instant.class),
                intOf(row, "total"), intOf(row, "valid"), intOf(row, "invalid"), intOf(row, "duplicate"),
                intOf(row, "sent"), intOf(row, "failed"),
                CnsReqBatch.Sts.valueOf(row.get("sts", String.class)),
                row.get("finished_at", Instant.class));
    }

    private CnsReqBatch.Row toRow(Readable row) {
        String typ = row.get("typ", String.class);
        return new CnsReqBatch.Row(intOf(row, "row_no"), row.get("contact", String.class), row.get("ref", String.class),
                typ == null ? null : ExtDpRefTyp.valueOf(typ), row.get("val", String.class),
                CnsReqBatch.Outcome.valueOf(row.get("outcome", String.class)), row.get("cns_req_id", String.class),
                row.get("problem", String.class));
    }

    private static int intOf(Readable row, String column) {
        Integer value = row.get(column, Integer.class);
        return value == null ? 0 : value;
    }

    private String writeDCats(CnsReqTemplate template) {
        try {
            return objectMapper.writeValueAsString(template.scp().dCats().stream().map(DCat::code).sorted().toList());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize data categories", e);
        }
    }

    private Scp readScp(String json) {
        try {
            List<String> codes = objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
            return new Scp(Set.copyOf(codes.stream()
                    .map(DCat::new).toList()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot read data categories", e);
        }
    }
}
