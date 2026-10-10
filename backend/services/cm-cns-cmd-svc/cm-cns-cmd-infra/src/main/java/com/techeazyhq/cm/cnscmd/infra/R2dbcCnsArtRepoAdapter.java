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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtFactory;
import com.techeazyhq.cm.cnscmd.dom.CnsArtRepoPort;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;
import com.techeazyhq.cm.cnscmd.dom.DCat;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.cnscmd.dom.Prps;
import com.techeazyhq.cm.cnscmd.dom.Scp;
import com.techeazyhq.cm.cnscmd.dom.ValPrd;
import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link CnsArtRepoPort} adapter (plan §12, Repository pattern). */
public class R2dbcCnsArtRepoAdapter implements CnsArtRepoPort {

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;
    private final ObxWriter obxWriter;

    public R2dbcCnsArtRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
        this.obxWriter = new ObxWriter(db, objectMapper);
    }

    @Override
    public Mono<CnsArt> save(CnsArt cnsArt) {
        String dCatsJson = writeDCats(cnsArt.scp());
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_cmd.cns_art
                            (id, cns_req_id, df_id, dp_ref_typ, dp_ref_val, dp_id, prps_code, d_cats,
                             val_from, val_until, sts, issued_at, cns_tkn)
                        values
                            (:id, :cnsReqId, :dfId, :dpRefTyp, :dpRefVal, :dpId, :prpsCode, cast(:dCats as jsonb),
                             :valFrom, :valUntil, :sts, :issuedAt, :cnsTkn)
                        on conflict (id) do update set
                            dp_ref_typ = excluded.dp_ref_typ,
                            dp_ref_val = excluded.dp_ref_val,
                            dp_id = excluded.dp_id,
                            sts = excluded.sts,
                            cns_tkn = excluded.cns_tkn
                        """)
                .bind("id", cnsArt.id())
                .bind("cnsReqId", cnsArt.cnsReqId())
                .bind("dfId", cnsArt.dfId())
                .bind("prpsCode", cnsArt.prps().code())
                .bind("dCats", dCatsJson)
                .bind("valFrom", cnsArt.valPrd().from())
                .bind("valUntil", cnsArt.valPrd().until())
                .bind("sts", cnsArt.sts().name())
                .bind("issuedAt", cnsArt.issuedAt());
        spec = R2dbcBinds.bindNullable(spec, "dpRefTyp", DpRefColumns.typOf(cnsArt.dpRef()), String.class);
        spec = R2dbcBinds.bindNullable(spec, "dpRefVal", DpRefColumns.valOf(cnsArt.dpRef()), String.class);
        spec = R2dbcBinds.bindNullable(spec, "dpId", DpRefColumns.dpIdOf(cnsArt.dpRef()), String.class);
        spec = R2dbcBinds.bindNullable(spec, "cnsTkn", cnsArt.cnsTkn(), String.class);

        Mono<Void> upsert = spec.then();

        List<DomainEvt> pending = cnsArt.pullEvts();
        Mono<Void> drainOutbox = Flux.fromIterable(pending)
                .concatMap(evt -> obxWriter.insert(evt, "CnsArt", cnsArt.id()))
                .then();

        return upsert.then(drainOutbox).thenReturn(cnsArt);
    }

    @Override
    public Mono<CnsArt> findById(String id) {
        return db.sql("select * from cns_cmd.cns_art where id = :id")
                .bind("id", id)
                .map(this::toDomain)
                .one();
    }

    @Override
    public Flux<CnsArt> findUnlinkedByExtRef(ExtDpRefTyp typ, String val) {
        return db.sql("select * from cns_cmd.cns_art where dp_id is null and dp_ref_typ = :typ and dp_ref_val = :val")
                .bind("typ", typ.name())
                .bind("val", val)
                .map(this::toDomain)
                .all();
    }

    @Override
    public Flux<CnsArt> findIssuedExpiredAsOf(Instant now) {
        return db.sql("select * from cns_cmd.cns_art where sts = 'ISSUED' and val_until < :now")
                .bind("now", now)
                .map(this::toDomain)
                .all();
    }

    private CnsArt toDomain(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        DpRef dpRef = DpRefColumns.fromRow(row);
        Scp scp = readScp(row.get("d_cats", String.class));
        return CnsArtFactory.rehydrate(
                row.get("id", String.class),
                row.get("cns_req_id", String.class),
                row.get("df_id", String.class),
                dpRef,
                new Prps(row.get("prps_code", String.class)),
                scp,
                new ValPrd(row.get("val_from", Instant.class), row.get("val_until", Instant.class)),
                CnsArtSts.valueOf(row.get("sts", String.class)),
                row.get("issued_at", Instant.class),
                row.get("cns_tkn", String.class));
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
