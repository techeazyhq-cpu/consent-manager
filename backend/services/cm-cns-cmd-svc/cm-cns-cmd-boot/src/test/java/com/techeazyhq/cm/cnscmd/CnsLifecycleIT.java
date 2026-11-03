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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.cnscmd.app.ApproveCnsCmdHandler;
import com.techeazyhq.cm.cnscmd.app.ApproveCnsReqCmd;
import com.techeazyhq.cm.cnscmd.app.CnsLifecycleSagaOrchestrator;
import com.techeazyhq.cm.cnscmd.app.CnsReqTerms;
import com.techeazyhq.cm.cnscmd.app.CnsTknIssuer;
import com.techeazyhq.cm.cnscmd.app.CreateCnsReqCmd;
import com.techeazyhq.cm.cnscmd.app.CreateCnsReqCmdHandler;
import com.techeazyhq.cm.cnscmd.app.LinkDpCmd;
import com.techeazyhq.cm.cnscmd.app.LinkDpCmdHandler;
import com.techeazyhq.cm.cnscmd.app.PrpsResolver;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmd;
import com.techeazyhq.cm.cnscmd.app.RevokeCnsArtCmdHandler;
import com.techeazyhq.cm.cnscmd.app.SgaRedriveJob;
import com.techeazyhq.cm.cnscmd.dom.CnsArt;
import com.techeazyhq.cm.cnscmd.dom.CnsArtSts;
import com.techeazyhq.cm.cnscmd.dom.CnsReq;
import com.techeazyhq.cm.cnscmd.dom.CnsReqSts;
import com.techeazyhq.cm.cnscmd.dom.DpRef;
import com.techeazyhq.cm.cnscmd.dom.ExtDpRefTyp;
import com.techeazyhq.cm.cnscmd.dom.NtfCmdPort;
import com.techeazyhq.cm.cnscmd.dom.PrpsDCat;
import com.techeazyhq.cm.cnscmd.dom.PrpsDef;
import com.techeazyhq.cm.cnscmd.dom.PrpsNtc;
import com.techeazyhq.cm.cnscmd.dom.PrpsSensitivity;
import com.techeazyhq.cm.cnscmd.dom.PrpsVerContent;
import com.techeazyhq.cm.cnscmd.dom.SgaStep;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsArtRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcCnsReqRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcObxAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcPrpsDefRepoAdapter;
import com.techeazyhq.cm.cnscmd.infra.R2dbcSgaStateAdapter;
import com.techeazyhq.cm.domkit.Spec;
import com.techeazyhq.cm.seckit.CnsTknKeys;
import com.techeazyhq.cm.seckit.CnsTknSigner;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Mono;

/**
 * The consent lifecycle against a real PostgreSQL (#126): what each step stores and puts in the outbox, linking a
 * principal who signs up after being asked, the subscription gate, and the saga's own state (ADR-005). cm-df-reg-svc,
 * cm-bil-svc and cm-ntf-svc are stand-ins; the outbox relay to Pulsar is covered in cm-df-reg-svc's flow test.
 */
@Testcontainers
class CnsLifecycleIT extends PgIntegrationTestBase {

    private static final String DF = "df-it";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final AtomicReference<Spec.SpecResult> SUBSCRIPTION = new AtomicReference<>();
    private static final AtomicBoolean NTF_UP = new AtomicBoolean(true);

    private static DatabaseClient db;
    private static R2dbcCnsReqRepoAdapter reqs;
    private static R2dbcCnsArtRepoAdapter arts;
    private static R2dbcSgaStateAdapter sgaState;
    private static CreateCnsReqCmdHandler create;
    private static ApproveCnsCmdHandler approve;
    private static RevokeCnsArtCmdHandler revoke;
    private static LinkDpCmdHandler linkDp;
    private static CnsLifecycleSagaOrchestrator saga;

    @BeforeAll
    static void setUp() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("cns_cmd").defaultSchema("cns_cmd").locations("classpath:db/migration")
                .load().migrate();
        db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build()));

        reqs = new R2dbcCnsReqRepoAdapter(db, JSON);
        arts = new R2dbcCnsArtRepoAdapter(db, JSON);
        sgaState = new R2dbcSgaStateAdapter(db);
        R2dbcObxAdapter obx = new R2dbcObxAdapter(db, JSON);
        R2dbcPrpsDefRepoAdapter purposes = new R2dbcPrpsDefRepoAdapter(db, JSON);
        Instant t0 = Instant.parse("2026-10-01T00:00:00Z");
        PrpsDef orderUpdates = PrpsDef.create(DF, "ORDER_UPDATES", new PrpsVerContent("Order updates", null,
                List.of(new PrpsDCat("EMAIL", "dpv-pd:EmailAddress", PrpsSensitivity.ORDINARY, true,
                                "Without your email we can't tell you when your order ships."),
                        new PrpsDCat("NAME", null, PrpsSensitivity.ORDINARY, false, null)),
                List.of(), List.of(), 365, 180, 365, Set.of(),
                List.of(new PrpsNtc("en", "Order updates", "We email you about your orders."))), t0);
        orderUpdates.publish(t0);
        purposes.save(orderUpdates).block();

        CnsTknSigner signer = new CnsTknSigner(CnsTknKeys.loadOrCreate(
                Files.createTempDirectory("cns-tkn-keys").resolve("jwks.json"), true, Clock.systemUTC()));
        create = new CreateCnsReqCmdHandler(reqs, dfId -> Mono.just(true), dfId -> Mono.just(SUBSCRIPTION.get()),
                new PrpsResolver(purposes));
        approve = new ApproveCnsCmdHandler(reqs, arts, obx,
                new CnsTknIssuer(signer, Duration.ofMinutes(15), Clock.systemUTC()), new CnsReqTerms(reqs, purposes));
        revoke = new RevokeCnsArtCmdHandler(arts);
        linkDp = new LinkDpCmdHandler(reqs, arts, obx);
        saga = new CnsLifecycleSagaOrchestrator(reqs, new FlakyNtf(), sgaState, Clock.systemUTC());
    }

    @BeforeEach
    void healthyNeighbours() {
        SUBSCRIPTION.set(Spec.SpecResult.ok());
        NTF_UP.set(true);
    }

    @Test
    void aRequestIsCreatedApprovedAndWithdrawnWithEachStepInTheOutbox() {
        CnsReq req = create.handle(request("dp-1", null, null)).block();
        assertNotNull(req);
        assertEquals(CnsReqSts.PENDING, reqs.findById(req.id()).block().sts());
        assertEquals(1L, outbox(req.id(), "CnsReqCreatedEvt"));

        CnsArt art = approve.handle(new ApproveCnsReqCmd(req.id(), "dp-1", Set.of("EMAIL"), 30)).block();
        assertNotNull(art);
        assertEquals(CnsReqSts.GRANTED, reqs.findById(req.id()).block().sts());
        CnsArt issued = arts.findById(art.id()).block();
        assertEquals(CnsArtSts.ISSUED, issued.sts());
        assertTrue(art.cnsTkn() != null && art.cnsTkn().split("\\.").length == 3, "a signed consent token");
        assertEquals(1L, outbox(art.id(), "CnsGrantedEvt"));

        revoke.handle(new RevokeCnsArtCmd(art.id(), "dp-1")).block();
        assertEquals(CnsArtSts.REVOKED, arts.findById(art.id()).block().sts());
        assertEquals(CnsArtSts.REVOKED, arts.findById(art.id()).block().liveStatus(Instant.now()));
        assertEquals(1L, outbox(art.id(), "CnsRevokedEvt"));
    }

    @Test
    void onlyThePrincipalTheRequestNamesCanApproveIt() {
        CnsReq req = create.handle(request("dp-1", null, null)).block();

        assertThrows(DomRuleViolation.class,
                () -> approve.handle(new ApproveCnsReqCmd(req.id(), "dp-someone-else", Set.of("EMAIL"), 30)).block());
        assertEquals(CnsReqSts.PENDING, reqs.findById(req.id()).block().sts());
    }

    @Test
    void aPrincipalAskedByEmailIsLinkedWhenTheySignUp() {
        String email = "new-" + System.nanoTime() + "@example.com";
        CnsReq req = create.handle(request(null, ExtDpRefTyp.EMAIL, email)).block();
        assertInstanceOf(DpRef.UnlnkDpRef.class, reqs.findById(req.id()).block().dpRef());

        Long linked = linkDp.handle(new LinkDpCmd("dp-new", ExtDpRefTyp.EMAIL, email)).block();

        assertEquals(1L, linked);
        DpRef.LnkdDpRef ref = assertInstanceOf(DpRef.LnkdDpRef.class, reqs.findById(req.id()).block().dpRef());
        assertEquals("dp-new", ref.dpId());
        assertEquals(1L, outbox(req.id(), "CnsDpLinkedEvt"));
        CnsArt art = approve.handle(new ApproveCnsReqCmd(req.id(), "dp-new", Set.of("EMAIL"), 30)).block();
        assertEquals(CnsArtSts.ISSUED, art.sts(), "once linked, they can decide");
    }

    @Test
    void aFiduciaryOverItsQuotaIsRefusedAndNothingIsStored() {
        SUBSCRIPTION.set(Spec.SpecResult.violated("quota-exceeded"));
        long before = count("cns_cmd.cns_req");

        DomRuleViolation refused = assertThrows(DomRuleViolation.class,
                () -> create.handle(request("dp-1", null, null)).block());

        assertEquals(429, refused.httpStatus());
        assertEquals(before, count("cns_cmd.cns_req"));
    }

    @Test
    void aFiduciaryWhoseSubscriptionIsInactiveIsRefused() {
        SUBSCRIPTION.set(Spec.SpecResult.violated("subscription-inactive"));

        DomRuleViolation refused = assertThrows(DomRuleViolation.class,
                () -> create.handle(request("dp-1", null, null)).block());

        assertEquals(402, refused.httpStatus());
    }

    @Test
    void aSagaLegThatFailsIsRecordedAndTheSweepRunsItAgain() {
        String cnsReqId = "req-saga-" + System.nanoTime();
        NTF_UP.set(false);

        assertThrows(RuntimeException.class, () -> saga.onCnsDecided(DF, cnsReqId, CnsReqSts.GRANTED).block());
        String sagaId = SgaStep.of(SgaStep.Typ.NOTIFY_DF_DECIDED, cnsReqId, DF, "GRANTED").sagaId();
        SgaStep failed = sgaState.findById(sagaId).block();
        assertEquals(SgaStep.Sts.FAILED, failed.sts());
        assertEquals(1, failed.attempts());
        assertTrue(failed.lastError().contains("cm-ntf-svc is down"));
        assertEquals(DF, failed.dfId());
        assertEquals("GRANTED", failed.outcome());

        NTF_UP.set(true);
        new SgaRedriveJob(sgaState, saga, Duration.ZERO, 10, Clock.systemUTC()).runOnce().block();

        SgaStep done = sgaState.findById(sagaId).block();
        assertEquals(SgaStep.Sts.DONE, done.sts());
        assertEquals(2, done.attempts());
    }

    @Test
    void theSweepGivesUpAfterItsAttemptsAndLeavesTheLegForAnOperator() {
        String cnsArtId = "art-saga-" + System.nanoTime();
        NTF_UP.set(false);
        SgaRedriveJob sweep = new SgaRedriveJob(sgaState, saga, Duration.ZERO, 3, Clock.systemUTC());

        assertThrows(RuntimeException.class, () -> saga.onCnsRevoked(DF, cnsArtId).block());
        sweep.runOnce().block();
        sweep.runOnce().block();
        String sagaId = SgaStep.of(SgaStep.Typ.NOTIFY_DF_REVOKED, cnsArtId, DF, "REVOKED").sagaId();
        assertEquals(3, sgaState.findById(sagaId).block().attempts());

        sweep.runOnce().block();

        assertEquals(SgaStep.Sts.GAVE_UP, sgaState.findById(sagaId).block().sts());
        sweep.runOnce().block();
        assertEquals(3, sgaState.findById(sagaId).block().attempts(), "a leg given up on is not run again");
    }

    @Test
    void aLegThatSucceedsFirstTimeIsDone() {
        String cnsReqId = "req-ok-" + System.nanoTime();

        saga.onCnsDecided(DF, cnsReqId, CnsReqSts.DENIED).block();

        SgaStep step = sgaState.findById(SgaStep.of(SgaStep.Typ.NOTIFY_DF_DECIDED, cnsReqId, DF, "DENIED").sagaId()).block();
        assertEquals(SgaStep.Sts.DONE, step.sts());
        assertEquals(1, step.attempts());
    }

    private static CreateCnsReqCmd request(String dpId, ExtDpRefTyp typ, String val) {
        return new CreateCnsReqCmd(DF, dpId, typ, val, "ORDER_UPDATES", 1, "en", null);
    }

    private static long outbox(String aggId, String evtType) {
        return db.sql("select count(*) from cns_cmd.outbox_evt where agg_id = :id and evt_type = :type")
                .bind("id", aggId).bind("type", evtType).map((row, meta) -> row.get(0, Long.class)).one().block();
    }

    private static long count(String table) {
        return db.sql("select count(*) from " + table).map((row, meta) -> row.get(0, Long.class)).one().block();
    }

    private static final class FlakyNtf implements NtfCmdPort {
        @Override
        public Mono<Void> notifyDp(CnsReq cnsReq) {
            return send();
        }

        @Override
        public Mono<Void> notifyDf(String dfId, String subjectId, String outcome) {
            return send();
        }

        private static Mono<Void> send() {
            return NTF_UP.get() ? Mono.empty() : Mono.error(new IllegalStateException("cm-ntf-svc is down"));
        }
    }
}
