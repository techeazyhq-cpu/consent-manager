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
package com.techeazyhq.cm.aud.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.aud.dom.AnomalyFinding;
import com.techeazyhq.cm.aud.dom.AnomalyPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Hourly counts read from {@code aud.entry} (ids, types and times only) and findings kept in {@code aud.anomaly} (ADR-112). */
public class R2dbcAnomalyAdapter implements AnomalyPort {

    private static final String COLUMNS = "id, df_id, signal, window_start, observed, expected, score, detected_at, status, acknowledged_by, acknowledged_at";

    private final DatabaseClient db;

    public R2dbcAnomalyAdapter(DatabaseClient db) {
        this.db = db;
    }

    private static OffsetDateTime at(Instant i) {
        return i.atOffset(ZoneOffset.UTC);
    }

    /** Hours are whole hours in UTC whatever time zone the database session is in: a zone with a half-hour offset would cut them at :30. */
    @Override
    public Flux<HourCount> hourlyCounts(Instant from, Instant to) {
        return db.sql("""
                        select df_id, evt_type, date_trunc('hour', occurred_at at time zone 'UTC') at time zone 'UTC' as hour, count(*) as n
                        from aud.entry
                        where occurred_at >= :from and occurred_at < :to and df_id is not null
                        group by df_id, evt_type, 3""")
                .bind("from", at(from))
                .bind("to", at(to))
                .map((row, meta) -> new HourCount(row.get("df_id", String.class), row.get("evt_type", String.class),
                        row.get("hour", OffsetDateTime.class).toInstant(), row.get("n", Long.class)))
                .all();
    }

    @Override
    public Mono<Boolean> save(AnomalyFinding f) {
        return db.sql("""
                        insert into aud.anomaly (id, df_id, signal, window_start, observed, expected, score, detected_at, status)
                        values (:id, :dfId, :signal, :windowStart, :observed, :expected, :score, :detectedAt, 'OPEN')
                        on conflict (df_id, signal, window_start) do nothing""")
                .bind("id", f.id())
                .bind("dfId", f.dfId())
                .bind("signal", f.signal())
                .bind("windowStart", at(f.windowStart()))
                .bind("observed", (int) Math.min(Integer.MAX_VALUE, f.observed()))
                .bind("expected", f.expected())
                .bind("score", f.score())
                .bind("detectedAt", at(f.detectedAt()))
                .fetch().rowsUpdated()
                .map(n -> n > 0);
    }

    @Override
    public Flux<AnomalyFinding> list(String status, int limit) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("select " + COLUMNS + " from aud.anomaly "
                + (status == null ? "" : "where status = :status ") + "order by detected_at desc limit :limit");
        if (status != null) {
            spec = spec.bind("status", status);
        }
        return spec.bind("limit", limit).map((row, meta) -> toFinding(row)).all();
    }

    @Override
    public Mono<Boolean> acknowledge(UUID id, String by, Instant when) {
        return db.sql("update aud.anomaly set status = 'ACKNOWLEDGED', acknowledged_by = :by, acknowledged_at = :at "
                        + "where id = :id and status = 'OPEN'")
                .bind("by", by)
                .bind("at", at(when))
                .bind("id", id)
                .fetch().rowsUpdated()
                .map(n -> n > 0);
    }

    private static AnomalyFinding toFinding(Row row) {
        OffsetDateTime ack = row.get("acknowledged_at", OffsetDateTime.class);
        return new AnomalyFinding(row.get("id", UUID.class), row.get("df_id", String.class), row.get("signal", String.class),
                row.get("window_start", OffsetDateTime.class).toInstant(), row.get("observed", Integer.class),
                row.get("expected", Double.class), row.get("score", Double.class), row.get("detected_at", OffsetDateTime.class).toInstant(),
                row.get("status", String.class), row.get("acknowledged_by", String.class), ack == null ? null : ack.toInstant());
    }
}
