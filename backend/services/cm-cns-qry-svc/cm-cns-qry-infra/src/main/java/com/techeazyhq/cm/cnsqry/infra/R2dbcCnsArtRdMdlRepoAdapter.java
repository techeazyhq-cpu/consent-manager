package com.techeazyhq.cm.cnsqry.infra;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdlRepoPort;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.DpDashboardRow;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcCnsArtRdMdlRepoAdapter implements CnsArtRdMdlRepoPort {

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;

    public R2dbcCnsArtRdMdlRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<CnsArtRdMdl> upsert(CnsArtRdMdl row) {
        String dCatsJson = writeDCats(row.dCatCodes());
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into cns_qry.cns_art_rd_mdl
                            (cns_req_id, cns_art_id, df_id, dp_id, prps_code, d_cats, lcy_sts, valid_from, valid_until, created_at, updated_at)
                        values
                            (:cnsReqId, :cnsArtId, :dfId, :dpId, :prpsCode, cast(:dCats as jsonb), :lcySts, :validFrom, :validUntil, :createdAt, :updatedAt)
                        on conflict (cns_req_id) do update set
                            cns_art_id = coalesce(excluded.cns_art_id, cns_art_rd_mdl.cns_art_id),
                            dp_id = coalesce(excluded.dp_id, cns_art_rd_mdl.dp_id),
                            lcy_sts = excluded.lcy_sts,
                            valid_from = coalesce(excluded.valid_from, cns_art_rd_mdl.valid_from),
                            valid_until = coalesce(excluded.valid_until, cns_art_rd_mdl.valid_until),
                            updated_at = excluded.updated_at
                        """)
                .bind("cnsReqId", row.cnsReqId())
                .bind("dfId", row.dfId())
                .bind("prpsCode", row.prpsCode())
                .bind("dCats", dCatsJson)
                .bind("lcySts", row.lcySts().name())
                .bind("createdAt", row.createdAt())
                .bind("updatedAt", row.updatedAt());
        spec = bindNullable(spec, "cnsArtId", row.cnsArtId(), String.class);
        spec = bindNullable(spec, "dpId", row.dpId(), String.class);
        spec = bindNullable(spec, "validFrom", row.validFrom(), Instant.class);
        spec = bindNullable(spec, "validUntil", row.validUntil(), Instant.class);

        return spec.then().thenReturn(row);
    }

    @Override
    public Mono<CnsArtRdMdl> findByCnsReqId(String cnsReqId) {
        return db.sql("select * from cns_qry.cns_art_rd_mdl where cns_req_id = :cnsReqId")
                .bind("cnsReqId", cnsReqId).map(this::toDomain).one();
    }

    @Override
    public Mono<CnsArtRdMdl> findByCnsArtId(String cnsArtId) {
        return db.sql("select * from cns_qry.cns_art_rd_mdl where cns_art_id = :cnsArtId")
                .bind("cnsArtId", cnsArtId).map(this::toDomain).one();
    }

    @Override
    public Flux<CnsArtRdMdl> findByDpId(String dpId) {
        return db.sql("select * from cns_qry.cns_art_rd_mdl where dp_id = :dpId order by updated_at desc")
                .bind("dpId", dpId).map(this::toDomain).all();
    }

    @Override
    public Flux<CnsArtRdMdl> findByDfId(String dfId) {
        return db.sql("select * from cns_qry.cns_art_rd_mdl where df_id = :dfId order by updated_at desc")
                .bind("dfId", dfId).map(this::toDomain).all();
    }

    @Override
    public Mono<Void> linkDp(String recordId, String recordType, String newDpId) {
        String column = "CnsReq".equals(recordType) ? "cns_req_id" : "cns_art_id";
        return db.sql("update cns_qry.cns_art_rd_mdl set dp_id = :newDpId where " + column + " = :recordId and dp_id is null")
                .bind("newDpId", newDpId).bind("recordId", recordId).then();
    }

    @Override
    public Flux<DpDashboardRow> dashboardFor(String dpId) {
        return db.sql("""
                        select df_id,
                               count(*) filter (where lcy_sts = 'ISSUED') as active_cnt,
                               count(*) filter (where lcy_sts = 'PENDING') as pending_cnt,
                               max(updated_at) as last_updated_at
                        from cns_qry.cns_art_rd_mdl
                        where dp_id = :dpId
                        group by df_id
                        """)
                .bind("dpId", dpId)
                .map((row, meta) -> new DpDashboardRow(
                        row.get("df_id", String.class),
                        row.get("active_cnt", Long.class),
                        row.get("pending_cnt", Long.class),
                        row.get("last_updated_at", Instant.class)))
                .all();
    }

    private CnsArtRdMdl toDomain(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        return new CnsArtRdMdl(
                row.get("cns_req_id", String.class),
                row.get("cns_art_id", String.class),
                row.get("df_id", String.class),
                row.get("dp_id", String.class),
                row.get("prps_code", String.class),
                readDCats(row.get("d_cats", String.class)),
                CnsLcySts.valueOf(row.get("lcy_sts", String.class)),
                row.get("valid_from", Instant.class),
                row.get("valid_until", Instant.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    private String writeDCats(Set<String> dCats) {
        try {
            return objectMapper.writeValueAsString(dCats);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize data categories", e);
        }
    }

    private Set<String> readDCats(String json) {
        if (json == null) {
            return Set.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Set<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize data categories", e);
        }
    }

    private <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }
}
