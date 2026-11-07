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

package com.techeazyhq.cm.cnscmd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.dom.CnsRcpt;
import com.techeazyhq.cm.cnscmd.dom.CnsRec;
import com.techeazyhq.cm.cnscmd.dom.CnsRecEvt;
import com.techeazyhq.cm.cnscmd.dom.CnsRecRepoPort.Pending;
import com.techeazyhq.cm.cnscmd.dom.CnsRecording;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsRecRepoAdapter;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * The receipt queue (V7) against a real PostgreSQL: an event joins it when it is appended, once, and leaves with its
 * receipt; the receipt job reads only the queue, so its cost no longer grows with every consent ever recorded. The
 * migration's backfill is checked too: events recorded before V7 without a receipt are queued, those with one aren't.
 */
@Testcontainers
class CnsRcptQueueIT extends PgIntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");

    private static DatabaseClient db;
    private static R2dbcCnsRecRepoAdapter recs;

    @BeforeAll
    static void setUp() {
        // Up to V6 first, with a record as the old code left it: two events, a receipt for the first only
        flyway("6").migrate();
        db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build()));
        recs = new R2dbcCnsRecRepoAdapter(db, JSON);
        request("req-old");
        db.sql("""
                insert into cns_cmd.cns_rec (id, cns_req_id, df_id, prps_code, created_at)
                values ('rec-old', 'req-old', 'df-1', 'ORDER_UPDATES', now())
                """).then().block();
        for (int seq = 1; seq <= 2; seq++) {
            db.sql("""
                    insert into cns_cmd.cns_rec_evt (rec_id, seq, evt_id, occurred_at, type, actor)
                    values ('rec-old', :seq, :evtId, :at, 'REQUESTED', 'FIDUCIARY')
                    """).bind("seq", seq).bind("evtId", UUID.randomUUID()).bind("at", T0.plusSeconds(seq))
                    .then().block();
        }
        db.sql("""
                insert into cns_cmd.cns_rcpt (id, rec_id, seq, issued_at, kid, jws, sha256)
                values ('rcpt-old-1', 'rec-old', 1, now(), 'kid', 'jws', repeat('0', 64))
                """).then().block();

        flyway(null).migrate();
    }

    /**
     * A bare consent request for a record to belong to. Its purpose version isn't what this test is about, so the
     * one insert skips foreign-key checks; the records, events and receipts under test keep theirs.
     */
    private static void request(String id) {
        db.sql("""
                set session_replication_role = replica;
                insert into cns_cmd.cns_req (id, df_id, prps_code, d_cats, ntc_title, sts, created_at, prps_ver_id,
                                             prps_ver_no, ntc_lang, ntc_hash)
                values ('%s', 'df-1', 'ORDER_UPDATES', '[]', 'Order updates', 'PENDING', now(), 'ver-1', 1, 'en',
                        repeat('0', 64));
                set session_replication_role = origin;
                """.formatted(id)).then().block();
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("cns_cmd").defaultSchema("cns_cmd").locations("classpath:db/migration");
        return (target == null ? config : config.target(target)).load();
    }

    @Test
    void theMigrationQueuesOnlyTheEventsRecordedBeforeItWithoutAReceipt() {
        assertEquals(List.of(new Pending("req-old", 2)), pendingFor("req-old"));
    }

    @Test
    void anEventIsQueuedOnceWhenAppendedAndLeavesWithItsReceipt() {
        String req = UUID.randomUUID().toString();
        request(req);
        CnsRecEvt requested = evt(CnsRecEvt.Type.REQUESTED, CnsRecEvt.Actor.FIDUCIARY, 10);
        CnsRecEvt given = evt(CnsRecEvt.Type.GIVEN, CnsRecEvt.Actor.PRINCIPAL, 20);
        CnsRec header = new CnsRec(UUID.randomUUID().toString(), req, "df-1", "dp-1", "ORDER_UPDATES", null, T0,
                List.of());
        recs.record(new CnsRecording.Open(req, header, requested)).block();
        recs.record(new CnsRecording.Append(req, given)).block();
        recs.record(new CnsRecording.Append(req, given)).block(); // a redelivery: neither recorded nor queued again

        assertEquals(List.of(new Pending(req, 1), new Pending(req, 2)), pendingFor(req));

        recs.saveReceipt(receipt(header, 1)).block();
        assertEquals(List.of(new Pending(req, 2)), pendingFor(req));

        recs.saveReceipt(receipt(header, 2)).block();
        assertEquals(List.of(), pendingFor(req));
    }

    private static List<Pending> pendingFor(String cnsReqId) {
        return recs.pendingReceipts(1000).filter(p -> p.cnsReqId().equals(cnsReqId)).collectList().block();
    }

    private static CnsRecEvt evt(CnsRecEvt.Type type, CnsRecEvt.Actor actor, int second) {
        return new CnsRecEvt(0, UUID.randomUUID(), T0.plusSeconds(second), type, actor, Set.of("EMAIL"), null, null,
                null);
    }

    private static CnsRcpt receipt(CnsRec rec, int seq) {
        return new CnsRcpt(UUID.randomUUID().toString(), rec.id(), rec.cnsReqId(), seq, Instant.now(), "kid", "jws",
                "0".repeat(64));
    }
}
