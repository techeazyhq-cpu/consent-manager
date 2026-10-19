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
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqFactory;
import com.techeazyhq.cm.cnscmd.dom.CnsReqRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;
import com.techeazyhq.cm.cnscmd.dom.CnsReqTemplate;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.cnscmd.dom.Prps;
import com.techeazyhq.cm.cnscmd.dom.Scp;
import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link CnsReqRepoPort} adapter (plan §12, Repository pattern) — R2DBC upsert + Outbox drain in one statement chain. */
public class R2dbcCnsReqRepoAdapter implements CnsReqRepoPort {

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;
    private final ObxWriter obxWriter;

    public R2dbcCnsReqRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
        this.obxWriter = new ObxWriter(db, objectMapper);
    }

    @Override
    public Mono<CnsReq> save(CnsReq cnsReq) {
        String dCatsJson = writeDCats(cnsReq.scp());
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.cns_req
                            (id, df_id, dp_ref_typ, dp_ref_val, dp_id, prps_code, d_cats, ntc_title, ntc_text, sts, created_at,
                             prps_ver_id, prps_ver_no, ntc_lang, ntc_hash)
                        values
                            (:id, :dfId, :dpRefTyp, :dpRefVal, :dpId, :prpsCode, cast(:dCats as jsonb), :ntcTitle, :ntcText, :sts, :createdAt,
                             :prpsVerId, :prpsVerNo, :ntcLang, :ntcHash)
                        on conflict (id) do update set
                            dp_ref_typ = excluded.dp_ref_typ,
                            dp_ref_val = excluded.dp_ref_val,
                            dp_id = excluded.dp_id,
                            sts = excluded.sts
                        """)
                .bind("id", cnsReq.id())
                .bind("dfId", cnsReq.dfId())
                .bind("prpsCode", cnsReq.prps().code())
                .bind("dCats", dCatsJson)
                .bind("ntcTitle", cnsReq.ntcTitle())
                .bind("ntcText", cnsReq.ntcText() == null ? "" : cnsReq.ntcText())
                .bind("sts", cnsReq.sts().name())
                .bind("createdAt", cnsReq.createdAt());
        spec = PrpsVerColumns.bind(spec, cnsReq.prpsVer());
        spec = R2dbcBinds.bindNullable(spec, "dpRefTyp", DpRefColumns.typOf(cnsReq.dpRef()), String.class);
        spec = R2dbcBinds.bindNullable(spec, "dpRefVal", DpRefColumns.valOf(cnsReq.dpRef()), String.class);
        spec = R2dbcBinds.bindNullable(spec, "dpId", DpRefColumns.dpIdOf(cnsReq.dpRef()), String.class);

        Mono<Void> upsert = spec.then();

        java.util.List<DomainEvt> pending = cnsReq.pullEvts();
        Mono<Void> drainOutbox = Flux.fromIterable(pending)
                .concatMap(evt -> obxWriter.insert(evt, "CnsReq", cnsReq.id()))
                .then();

        return upsert.then(drainOutbox).thenReturn(cnsReq);
    }

    @Override
    public Mono<CnsReq> findById(String id) {
        return db.sql("select * from cns_cmd.cns_req where id = :id")
                .bind("id", id)
                .map(this::toDomain)
                .one();
    }

    @Override
    public Flux<CnsReq> findUnlinkedByExtRef(ExtDpRefTyp typ, String val) {
        return db.sql("select * from cns_cmd.cns_req where dp_id is null and dp_ref_typ = :typ and dp_ref_val = :val")
                .bind("typ", typ.name())
                .bind("val", val)
                .map(this::toDomain)
                .all();
    }

    private CnsReq toDomain(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        DpRef dpRef = DpRefColumns.fromRow(row);
        Scp scp = readScp(row.get("d_cats", String.class));
        return CnsReqFactory.rehydrate(
                row.get("id", String.class),
                row.get("df_id", String.class),
                dpRef,
                new CnsReqTemplate(new Prps(row.get("prps_code", String.class)), scp, row.get("ntc_title", String.class),
                        row.get("ntc_text", String.class), PrpsVerColumns.read(row)),
                CnsReqSts.valueOf(row.get("sts", String.class)),
                row.get("created_at", Instant.class));
    }

    private String writeDCats(Scp scp) {
        try {
            return objectMapper.writeValueAsString(scp.dCats().stream().map(DCat::code).collect(Collectors.toSet()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize data categories", e);
        }
    }

    private Scp readScp(String json) {
        try {
            Set<String> codes = objectMapper.readValue(json, new TypeReference<Set<String>>() {
            });
            return new Scp(codes.stream().map(DCat::new).collect(Collectors.toSet()));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize data categories", e);
        }
    }

}
