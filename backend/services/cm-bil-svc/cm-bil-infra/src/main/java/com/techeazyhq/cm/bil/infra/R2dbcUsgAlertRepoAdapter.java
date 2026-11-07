package com.techeazyhq.cm.bil.infra;

import java.time.Instant;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.bil.dom.UsgAlert;
import com.techeazyhq.cm.bil.dom.UsgAlertRepoPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcUsgAlertRepoAdapter implements UsgAlertRepoPort {

    private final DatabaseClient db;

    public R2dbcUsgAlertRepoAdapter(DatabaseClient db) {
        this.db = db;
    }

    /** The primary key {@code (df_id, period, threshold)} makes "already raised" a no-op insert. */
    @Override
    public Mono<Boolean> addIfNew(UsgAlert a) {
        return db.sql("""
                        insert into bil.usg_alert (df_id, period, threshold, used, quota, pln_name, raised_at)
                        values (:dfId, :period, :threshold, :used, :quota, :plnName, :raisedAt)
                        on conflict (df_id, period, threshold) do nothing
                        """)
                .bind("dfId", a.dfId())
                .bind("period", a.period())
                .bind("threshold", a.threshold())
                .bind("used", a.used())
                .bind("quota", a.quota())
                .bind("plnName", a.planName())
                .bind("raisedAt", a.raisedAt())
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    @Override
    public Flux<UsgAlert> findByDf(String dfId, int limit) {
        return db.sql("select * from bil.usg_alert where df_id = :dfId order by raised_at desc limit :limit")
                .bind("dfId", dfId)
                .bind("limit", limit)
                .map(R2dbcUsgAlertRepoAdapter::row)
                .all();
    }

    @Override
    public Flux<UsgAlert> findByPeriod(String period) {
        return db.sql("select * from bil.usg_alert where period = :period order by raised_at desc")
                .bind("period", period)
                .map(R2dbcUsgAlertRepoAdapter::row)
                .all();
    }

    private static UsgAlert row(Readable r) {
        return new UsgAlert(r.get("df_id", String.class), r.get("period", String.class).trim(),
                r.get("threshold", Integer.class), r.get("used", Long.class), r.get("quota", Long.class),
                r.get("pln_name", String.class), r.get("raised_at", Instant.class));
    }
}
