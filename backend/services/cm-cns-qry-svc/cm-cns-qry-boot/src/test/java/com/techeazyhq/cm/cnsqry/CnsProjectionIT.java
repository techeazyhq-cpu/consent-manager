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

package com.techeazyhq.cm.cnsqry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnsqry.app.DpDashboardQryHandler;
import com.techeazyhq.cm.cnsqry.app.Projector;
import com.techeazyhq.cm.cnsqry.dom.CnsArtRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsHstRdMdl;
import com.techeazyhq.cm.cnsqry.dom.CnsLcySts;
import com.techeazyhq.cm.cnsqry.dom.DpDashboardRow;
import com.techeazyhq.cm.cnsqry.infra.PulsarCnsEvtConsumer;
import com.techeazyhq.cm.cnsqry.infra.R2dbcCnsArtRdMdlRepoAdapter;
import com.techeazyhq.cm.cnsqry.infra.R2dbcCnsHstRdMdlRepoAdapter;
import com.techeazyhq.cm.cnsqry.infra.R2dbcPrjCkptAdapter;
import com.techeazyhq.cm.evtkit.PulsarTopics;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;
import com.techeazyhq.cm.testkit.PulsarTestBroker;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * The read model built from cm-cns-cmd-svc's events (#129): events go through a real Pulsar to the service's own
 * consumer and projector, into a real PostgreSQL. Each event type has its own topic, so these tests wait for the
 * projection rather than assume an order.
 */
@Testcontainers
class CnsProjectionIT extends PgIntegrationTestBase {

    @Container
    static final PulsarContainer PULSAR = PulsarTestBroker.container();

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final Duration PROJECTED_WITHIN = Duration.ofSeconds(30);

    private static DatabaseClient db;
    private static PulsarClient pulsarClient;
    private static R2dbcCnsArtRdMdlRepoAdapter rdMdl;
    private static R2dbcCnsHstRdMdlRepoAdapter hst;
    private static DpDashboardQryHandler dashboard;

    @BeforeAll
    static void setUp() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("cns_qry").defaultSchema("cns_qry").locations("classpath:db/migration")
                .load().migrate();
        db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build()));
        PulsarTestBroker.createNamespaces(PULSAR, "cnscmd");
        pulsarClient = PulsarClient.builder().serviceUrl(PULSAR.getPulsarBrokerUrl()).build();

        rdMdl = new R2dbcCnsArtRdMdlRepoAdapter(db, JSON);
        hst = new R2dbcCnsHstRdMdlRepoAdapter(db);
        dashboard = new DpDashboardQryHandler(rdMdl);
        Projector projector = new Projector(rdMdl, hst, new R2dbcPrjCkptAdapter(db));
        new PulsarCnsEvtConsumer(AdaptedReactivePulsarClientFactory.create(pulsarClient), JSON, projector).start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pulsarClient != null) {
            pulsarClient.close();
        }
    }

    @Test
    void aRequestGrantedThenWithdrawnEndsRevokedWithItsWholeHistory() throws Exception {
        String req = id("req");
        String art = id("art");
        Instant t = Instant.now();

        publish("cns-req-created", created(req, "df-a", linked("dp-1"), t));
        publish("cns-granted", granted(req, art, "df-a", "dp-1", Set.of("EMAIL"), t.plusSeconds(1)));
        publish("cns-revoked", Map.of("evtId", id("evt"), "occurredAt", t.plusSeconds(2), "cnsArtId", art,
                "dpRef", linked("dp-1"), "dfId", "df-a"));

        CnsArtRdMdl row = await(() -> rdMdl.findByCnsReqId(req).block(),
                r -> r != null && r.lcySts() == CnsLcySts.REVOKED);
        assertEquals(art, row.cnsArtId());
        assertEquals("dp-1", row.dpId());
        assertEquals(Set.of("EMAIL"), row.grantedDCatCodes());
        List<CnsLcySts> history = await(() -> hst.findByCnsReqId(req).map(CnsHstRdMdl::toSts).collectList().block(),
                h -> h.size() == 3);
        assertEquals(Set.of(CnsLcySts.PENDING, CnsLcySts.ISSUED, CnsLcySts.REVOKED), Set.copyOf(history));
    }

    @Test
    void aGrantDeliveredBeforeItsRequestIsStillProjected() throws Exception {
        String req = id("req");
        Instant t = Instant.now();

        publish("cns-granted", granted(req, id("art"), "df-a", "dp-2", Set.of("EMAIL", "NAME"), t.plusSeconds(1)));
        Thread.sleep(500);
        publish("cns-req-created", created(req, "df-a", linked("dp-2"), t));

        await(() -> rdMdl.findByCnsReqId(req).block(), r -> r != null && r.lcySts() == CnsLcySts.ISSUED);
    }

    @Test
    void anEventDeliveredTwiceIsProjectedOnce() throws Exception {
        String req = id("req");
        Map<String, Object> evt = created(req, "df-a", linked("dp-3"), Instant.now());

        publish("cns-req-created", evt);
        publish("cns-req-created", evt);

        await(() -> rdMdl.findByCnsReqId(req).block(), r -> r != null);
        Thread.sleep(1000);
        assertEquals(1, hst.findByCnsReqId(req).collectList().block().size());
    }

    @Test
    void aPrincipalAskedByEmailSeesTheRequestOnceLinked() throws Exception {
        String req = id("req");
        String dp = id("dp");
        publish("cns-req-created", created(req, "df-a",
                Map.of("typ", "EMAIL", "val", dp + "@example.com"), Instant.now()));
        await(() -> rdMdl.findByCnsReqId(req).block(), r -> r != null);
        assertTrue(dashboard.handle(dp).collectList().block().isEmpty(), "not theirs until linked");

        publish("cns-dp-linked", Map.of("evtId", id("evt"), "occurredAt", Instant.now(), "recordId", req,
                "recordType", "CnsReq", "newDpId", dp));

        List<DpDashboardRow> rows = await(() -> dashboard.handle(dp).collectList().block(), r -> !r.isEmpty());
        assertEquals("df-a", rows.get(0).dfId());
        assertEquals(1, rows.get(0).pendingCnt());
    }

    @Test
    void theDashboardCountsEachFiduciarysActiveAndPendingConsents() throws Exception {
        String dp = id("dp");
        Instant t = Instant.now();
        String given1 = id("req");
        String given2 = id("req");
        publish("cns-req-created", created(given1, "df-a", linked(dp), t));
        publish("cns-req-created", created(given2, "df-a", linked(dp), t));
        publish("cns-req-created", created(id("req"), "df-a", linked(dp), t));
        publish("cns-req-created", created(id("req"), "df-b", linked(dp), t));
        publish("cns-granted", granted(given1, id("art"), "df-a", dp, Set.of("EMAIL"), t.plusSeconds(1)));
        publish("cns-granted", granted(given2, id("art"), "df-a", dp, Set.of("EMAIL"), t.plusSeconds(1)));

        List<DpDashboardRow> rows = await(() -> dashboard.handle(dp).collectList().block(), r -> r.size() == 2
                && r.stream().anyMatch(row -> row.dfId().equals("df-a") && row.activeCnt() == 2));
        DpDashboardRow a = rows.stream().filter(row -> row.dfId().equals("df-a")).findFirst().orElseThrow();
        DpDashboardRow b = rows.stream().filter(row -> row.dfId().equals("df-b")).findFirst().orElseThrow();
        assertEquals(2, a.activeCnt());
        assertEquals(1, a.pendingCnt());
        assertEquals(0, b.activeCnt());
        assertEquals(1, b.pendingCnt());
    }

    private static Map<String, Object> created(String req, String df, Map<String, Object> dpRef, Instant at) {
        return Map.of("evtId", id("evt"), "occurredAt", at, "cnsReqId", req, "dfId", df, "dpRef", dpRef,
                "prps", Map.of("code", "ORDER_UPDATES"),
                "scp", Map.of("dCats", List.of(Map.of("code", "EMAIL"), Map.of("code", "NAME"))),
                "ntcTitle", "Order updates", "ntcText", "We email you about your orders.");
    }

    private static Map<String, Object> granted(String req, String art, String df, String dp, Set<String> dCats,
                                               Instant at) {
        return Map.of("evtId", id("evt"), "occurredAt", at, "cnsReqId", req, "cnsArtId", art, "dfId", df,
                "dpRef", linked(dp), "cnsTkn", "header.payload.signature",
                "valPrd", Map.of("from", at, "until", at.plus(Duration.ofDays(30))),
                "scp", Map.of("dCats", dCats.stream().map(code -> Map.of("code", code)).toList()),
                "validityDays", 30);
    }

    private static Map<String, Object> linked(String dpId) {
        return Map.of("dpId", dpId);
    }

    private static void publish(String eventNameKebab, Map<String, Object> payload) throws Exception {
        try (Producer<String> producer = pulsarClient.newProducer(Schema.STRING)
                .topic(PulsarTopics.evt("cnscmd", eventNameKebab)).create()) {
            producer.send(JSON.writeValueAsString(payload));
        }
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static <T> T await(Supplier<T> read, java.util.function.Predicate<T> done) throws InterruptedException {
        long deadline = System.nanoTime() + PROJECTED_WITHIN.toNanos();
        T last = null;
        while (System.nanoTime() < deadline) {
            last = read.get();
            if (done.test(last)) {
                return last;
            }
            Thread.sleep(200);
        }
        fail("not projected within " + PROJECTED_WITHIN + "; last read: " + last);
        return last;
    }
}
