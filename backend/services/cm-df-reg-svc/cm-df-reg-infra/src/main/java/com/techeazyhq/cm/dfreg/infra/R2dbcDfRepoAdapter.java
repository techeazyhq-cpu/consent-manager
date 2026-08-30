package com.techeazyhq.cm.dfreg.infra;

import java.time.Instant;
import java.util.List;

import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfRepoPort;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.domkit.DomainEvt;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcDfRepoAdapter implements DfRepoPort {

    private final DatabaseClient db;
    private final ObxWriter obxWriter;

    public R2dbcDfRepoAdapter(DatabaseClient db, ObjectMapper objectMapper) {
        this.db = db;
        this.obxWriter = new ObxWriter(db, objectMapper);
    }

    @Override
    public Mono<Df> save(Df df) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        insert into df_reg.df (id, name, contact_email, sts, registered_at, cm_df_api_client_secret, activated_at)
                        values (:id, :name, :contactEmail, :sts, :registeredAt, :clientSecret, :activatedAt)
                        on conflict (id) do update set
                            sts = excluded.sts,
                            cm_df_api_client_secret = excluded.cm_df_api_client_secret,
                            activated_at = excluded.activated_at
                        """)
                .bind("id", df.id())
                .bind("name", df.name())
                .bind("contactEmail", df.contactEmail())
                .bind("sts", df.sts().name())
                .bind("registeredAt", df.registeredAt());
        spec = bindNullable(spec, "clientSecret", df.cmDfApiClientSecret());
        spec = bindNullableInstant(spec, "activatedAt", df.activatedAt());

        Mono<Void> upsert = spec.then();
        List<DomainEvt> pending = df.pullEvts();
        Mono<Void> drainOutbox = Flux.fromIterable(pending).concatMap(evt -> obxWriter.insert(evt, "Df", df.id())).then();

        return upsert.then(drainOutbox).thenReturn(df);
    }

    @Override
    public Mono<Df> findById(String id) {
        return db.sql("select * from df_reg.df where id = :id")
                .bind("id", id)
                .map(this::toDomain)
                .one();
    }

    @Override
    public Flux<Df> findAll() {
        return db.sql("select * from df_reg.df order by registered_at desc").map(this::toDomain).all();
    }

    private Df toDomain(io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) {
        return DfFactory.rehydrate(
                row.get("id", String.class),
                row.get("name", String.class),
                row.get("contact_email", String.class),
                DfSts.valueOf(row.get("sts", String.class)),
                row.get("registered_at", Instant.class),
                row.get("cm_df_api_client_secret", String.class),
                row.get("activated_at", Instant.class));
    }

    private DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return value == null ? spec.bindNull(name, String.class) : spec.bind(name, value);
    }

    private DatabaseClient.GenericExecuteSpec bindNullableInstant(DatabaseClient.GenericExecuteSpec spec, String name, Instant value) {
        return value == null ? spec.bindNull(name, Instant.class) : spec.bind(name, value);
    }
}
