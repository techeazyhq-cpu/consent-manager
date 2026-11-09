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
package com.techeazyhq.cm.aud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.techeazyhq.cm.aud.dom.AnomalyFinding;
import com.techeazyhq.cm.aud.dom.AnomalyPort;
import com.techeazyhq.cm.aud.infra.R2dbcAnomalyAdapter;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/** The detector's SQL against a real PostgreSQL migrated by the service's own scripts (ADR-112). */
@Testcontainers
class R2dbcAnomalyAdapterIT extends PgIntegrationTestBase {

    private static DatabaseClient db;
    private static R2dbcAnomalyAdapter adapter;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("aud").defaultSchema("aud").locations("classpath:db/migration")
                .load().migrate();
        ConnectionFactory cf = ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build());
        db = DatabaseClient.create(cf);
        adapter = new R2dbcAnomalyAdapter(db);
    }

    @BeforeEach
    void startEmpty() {
        db.sql("delete from aud.anomaly").then().block();
    }

    private static void entry(long seq, String type, String df, String at) {
        db.sql("""
                        insert into aud.entry (seq, evt_id, evt_type, occurred_at, recorded_at, df_id, payload_sha256, prev_hash, entry_hash)
                        values (:seq, :id, :type, :at::timestamptz, :at::timestamptz, :df, repeat('a', 64), repeat('b', 64), :hash)
                        on conflict do nothing""")
                .bind("seq", seq).bind("id", UUID.randomUUID()).bind("type", type).bind("at", at)
                .bindNull("df", String.class).bind("hash", String.format("%064x", seq))
                .then().block();
        if (df != null) {
            db.sql("update aud.entry set df_id = :df where seq = :seq").bind("df", df).bind("seq", seq).then().block();
        }
    }

    @Test
    void countsEventsPerFiduciaryTypeAndHourAndIgnoresWhatHasNoFiduciaryOrIsOutsideTheWindow() {
        db.sql("alter table aud.entry disable trigger all").then().block();
        db.sql("delete from aud.entry").then().block();
        entry(1, "api-key.issued", "df-1", "2026-11-09T09:05:00Z");
        entry(2, "api-key.issued", "df-1", "2026-11-09T09:55:00Z");
        entry(3, "api-key.issued", "df-1", "2026-11-09T10:00:00Z"); // the next hour, and the end is excluded
        entry(4, "CnsReqCreatedEvt", "df-1", "2026-11-09T09:30:00Z");
        entry(5, "api-key.issued", "df-2", "2026-11-09T09:30:00Z");
        entry(6, "batch.finished", null, "2026-11-09T09:30:00Z"); // no fiduciary
        entry(7, "api-key.issued", "df-1", "2026-11-09T08:59:59Z"); // before the window
        db.sql("alter table aud.entry enable trigger all").then().block();

        List<AnomalyPort.HourCount> counts = adapter.hourlyCounts(Instant.parse("2026-11-09T09:00:00Z"), Instant.parse("2026-11-09T10:00:00Z"))
                .collectList().block();

        assertEquals(3, counts.size(), counts.toString());
        assertTrue(counts.contains(new AnomalyPort.HourCount("df-1", "api-key.issued", Instant.parse("2026-11-09T09:00:00Z"), 2)));
        assertTrue(counts.contains(new AnomalyPort.HourCount("df-1", "CnsReqCreatedEvt", Instant.parse("2026-11-09T09:00:00Z"), 1)));
        assertTrue(counts.contains(new AnomalyPort.HourCount("df-2", "api-key.issued", Instant.parse("2026-11-09T09:00:00Z"), 1)));
    }

    private static AnomalyFinding finding(String df, String signal, String hour) {
        return new AnomalyFinding(UUID.randomUUID(), df, signal, Instant.parse(hour), 400, 4.25, 80.5, Instant.parse("2026-11-09T10:10:00Z"),
                AnomalyFinding.OPEN, null, null);
    }

    @Test
    void aFindingIsKeptOnceForAFiduciarySignalAndHourAndCanBeAcknowledgedOnce() {
        AnomalyFinding f = finding("df-1", "api-keys-issued", "2026-11-09T09:00:00Z");
        assertTrue(adapter.save(f).block());
        assertFalse(adapter.save(finding("df-1", "api-keys-issued", "2026-11-09T09:00:00Z")).block(), "the same hour again is not new");
        assertTrue(adapter.save(finding("df-1", "api-keys-issued", "2026-11-09T10:00:00Z")).block(), "another hour is");

        assertEquals(2, adapter.list(AnomalyFinding.OPEN, 10).collectList().block().size());
        AnomalyFinding read = adapter.list(AnomalyFinding.OPEN, 10).filter(x -> x.id().equals(f.id())).blockFirst();
        assertEquals(400, read.observed());
        assertEquals(4.25, read.expected(), 0.0001);

        assertTrue(adapter.acknowledge(f.id(), "admin-1", Instant.parse("2026-11-09T11:00:00Z")).block());
        assertFalse(adapter.acknowledge(f.id(), "admin-2", Instant.parse("2026-11-09T11:05:00Z")).block(), "only an open finding is acknowledged");
        assertEquals(1, adapter.list(AnomalyFinding.OPEN, 10).collectList().block().size());
        AnomalyFinding done = adapter.list(AnomalyFinding.ACKNOWLEDGED, 10).blockFirst();
        assertEquals("admin-1", done.acknowledgedBy());
        assertEquals(2, adapter.list(null, 10).collectList().block().size());
    }
}
