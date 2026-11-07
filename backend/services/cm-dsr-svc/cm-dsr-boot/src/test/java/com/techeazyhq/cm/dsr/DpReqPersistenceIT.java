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

package com.techeazyhq.cm.dsr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.dsr.app.DpReqHandler;
import com.techeazyhq.cm.dsr.app.DsrCaller;
import com.techeazyhq.cm.dsr.app.DsrDeadlines;
import com.techeazyhq.cm.dsr.dom.DpCnsPort;
import com.techeazyhq.cm.dsr.dom.DpReq;
import com.techeazyhq.cm.dsr.dom.DpRgt;
import com.techeazyhq.cm.dsr.dom.OpenDpReqExistsException;
import com.techeazyhq.cm.dsr.infra.R2dbcDpReqRepoAdapter;
import com.techeazyhq.cm.dsr.infra.R2dbcDsrOutbox;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * Principal requests against a real PostgreSQL: the migration, one open request per consent and right even when two
 * arrive together, the order each side works its queue in, and a relayed request's message for the fiduciary written
 * to the outbox with it. The relay to Pulsar is the same as billing's, tested there.
 */
class DpReqPersistenceIT extends PgIntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static DatabaseClient db;
    private static R2dbcDpReqRepoAdapter repo;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("dsr").defaultSchema("dsr").locations("classpath:db/migration")
                .load().migrate();
        db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build()));
        repo = new R2dbcDpReqRepoAdapter(db);
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void aRequestReadsBackAsItWasSaved() {
        DpReq req = DpReq.relay(id("dp"), id("df"), id("cns"), "MARKETING", DpRgt.CORRECTION, "New address",
                Duration.ofDays(90), Instant.parse("2026-11-07T10:00:00Z"));
        repo.save(req).block();
        req.refuse("u-1", "admin@df", "We can't verify the new address", "Identity not verified", Instant.now());
        repo.save(req).block();

        DpReq read = repo.findById(req.id()).block();
        assertEquals(DpReq.Sts.REFUSED, read.sts());
        assertEquals(DpRgt.CORRECTION, read.rgt());
        assertEquals("New address", read.details());
        assertEquals("Identity not verified", read.refusalGround());
        assertEquals(req.dueBy(), read.dueBy());
        assertEquals(0L, repo.countOpenByDpId(req.dpId()).block());
    }

    @Test
    void oneOpenRequestPerConsentAndRight() {
        String dp = id("dp");
        String df = id("df");
        String cns = id("cns");
        Instant now = Instant.now();
        repo.save(DpReq.relay(dp, df, cns, null, DpRgt.ERASURE, null, Duration.ofDays(90), now)).block();

        assertThrows(OpenDpReqExistsException.class,
                () -> repo.save(DpReq.relay(dp, df, cns, null, DpRgt.ERASURE, null, Duration.ofDays(90), now)).block());
        // another right on the same consent is another request
        repo.save(DpReq.relay(dp, df, cns, null, DpRgt.ACCESS, null, Duration.ofDays(90), now)).block();
        assertEquals(2L, repo.countOpenByDpId(dp).block());
    }

    @Test
    void openRequestsComeFirstSoonestDueAndClosedOnesAfter() {
        String df = id("df");
        Instant now = Instant.now();
        DpReq later = DpReq.relay(id("dp"), df, id("cns"), null, DpRgt.ACCESS, null, Duration.ofDays(60), now);
        DpReq sooner = DpReq.relay(id("dp"), df, id("cns"), null, DpRgt.ACCESS, null, Duration.ofDays(10), now);
        DpReq closed = DpReq.relay(id("dp"), df, id("cns"), null, DpRgt.ACCESS, null, Duration.ofDays(1), now);
        closed.resolve("u", "u@df", "Sent", now);
        List.of(later, sooner, closed).forEach(r -> repo.save(r).block());

        List<String> all = repo.findByDfId(df, null, 10).map(DpReq::id).collectList().block();
        assertEquals(List.of(sooner.id(), later.id(), closed.id()), all);
        assertEquals(List.of(closed.id()), repo.findByDfId(df, DpReq.Sts.RESOLVED, 10).map(DpReq::id).collectList().block());
        assertTrue(repo.findGrievances(null, 500).all(r -> r.dfId() == null).block());
    }

    @Test
    void aRelayedRequestsMessageIsInTheOutboxWithIt() {
        String dp = id("dp");
        String cns = id("cns");
        String df = id("df");
        DpCnsPort consents = (cnsReqId, token) -> Mono.just(new DpCnsPort.DpCns(cnsReqId, df, "KYC"));
        R2dbcDsrOutbox outbox = new R2dbcDsrOutbox(db, JSON, null);
        DpReqHandler handler = new DpReqHandler(repo, consents, outbox, AuditTrail.to("dsr", outbox::enqueue),
                DsrDeadlines.DPDPA, Clock.systemUTC());
        DsrCaller principal = new DsrCaller(dp, dp, dp, null, false, false, "token");

        DpReq req = handler.relay(principal, cns, "ACCESS", null).block();

        List<String> types = db.sql("select evt_type, payload_json from dsr.outbox_evt where agg_id = :id order by created_at")
                .bind("id", req.id())
                .map(row -> row.get("evt_type", String.class) + " " + row.get("payload_json", String.class))
                .all().collectList().block();
        assertEquals(2, types.size(), types.toString());
        assertTrue(types.stream().anyMatch(t -> t.startsWith("DfMessage") && t.contains("principal_request.received")
                && t.contains(cns) && !t.contains(dp)), types.toString());
        assertTrue(types.stream().anyMatch(t -> t.startsWith("PlatformActionEvt") && t.contains("dp-request.raised")),
                types.toString());

        DomRuleViolation again = assertThrows(DomRuleViolation.class,
                () -> handler.relay(principal, cns, "ACCESS", null).block());
        assertEquals(409, again.httpStatus());
        assertFalse(repo.findByDpId(dp, 10).collectList().block().isEmpty());
    }
}
