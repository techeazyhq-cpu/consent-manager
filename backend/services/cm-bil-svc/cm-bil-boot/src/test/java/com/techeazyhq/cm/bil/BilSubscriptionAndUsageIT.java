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

package com.techeazyhq.cm.bil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.bil.app.OnDfActivatedEvtHandler;
import com.techeazyhq.cm.bil.app.SubEligQryHandler;
import com.techeazyhq.cm.bil.app.SubEligResult;
import com.techeazyhq.cm.bil.app.UsgReservationHandler;
import com.techeazyhq.cm.bil.dom.DfSub;
import com.techeazyhq.cm.bil.dom.DfSubSts;
import com.techeazyhq.cm.bil.infra.PulsarDfActivatedConsumer;
import com.techeazyhq.cm.bil.infra.R2dbcDfSubRepoAdapter;
import com.techeazyhq.cm.bil.infra.R2dbcSubPlnRepoAdapter;
import com.techeazyhq.cm.bil.infra.RedisUsgCtrAdapter;
import com.techeazyhq.cm.evtkit.PulsarTopics;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;
import com.techeazyhq.cm.testkit.PulsarTestBroker;
import com.techeazyhq.cm.webkit.DomRuleViolation;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;

/**
 * A fiduciary's subscription and usage (#50) against a real PostgreSQL, Redis and Pulsar: cm-df-reg-svc's
 * {@code DfActivatedEvt} subscribes it on its chosen plan, usage counts up in Redis, and a month's allowance can't be
 * overspent. The per-second limit is cm-gw's, tested there.
 */
@Testcontainers
class BilSubscriptionAndUsageIT extends PgIntegrationTestBase {

    @Container
    static final PulsarContainer PULSAR = PulsarTestBroker.container();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private static PulsarClient pulsarClient;
    private static LettuceConnectionFactory redis;
    private static R2dbcDfSubRepoAdapter subs;
    private static RedisUsgCtrAdapter usage;
    private static SubEligQryHandler eligibility;
    private static UsgReservationHandler reservations;

    @BeforeAll
    static void setUp() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("bil").defaultSchema("bil").locations("classpath:db/migration")
                .load().migrate();
        DatabaseClient db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build()));
        redis = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        redis.afterPropertiesSet();
        PulsarTestBroker.createNamespaces(PULSAR, "dfreg");
        pulsarClient = PulsarClient.builder().serviceUrl(PULSAR.getPulsarBrokerUrl()).build();

        subs = new R2dbcDfSubRepoAdapter(db);
        R2dbcSubPlnRepoAdapter plans = new R2dbcSubPlnRepoAdapter(db);
        usage = new RedisUsgCtrAdapter(new ReactiveStringRedisTemplate(redis));
        eligibility = new SubEligQryHandler(subs, plans, usage);
        reservations = new UsgReservationHandler(subs, plans, usage);
        new PulsarDfActivatedConsumer(AdaptedReactivePulsarClientFactory.create(pulsarClient), JSON,
                new OnDfActivatedEvtHandler(subs, plans)).start();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pulsarClient != null) {
            pulsarClient.close();
        }
        if (redis != null) {
            redis.destroy();
        }
    }

    @Test
    void anActivatedFiduciaryIsSubscribedOnceToThePlanItChose() throws Exception {
        String df = id("df");
        Map<String, Object> evt = activated(df, "STANDARD");

        publish(evt);
        publish(evt);

        DfSub sub = awaitSubscription(df);
        assertEquals(DfSubSts.ACTIVE, sub.sts());
        SubEligResult elig = eligibility.handle(df).block();
        assertEquals(50_000, elig.reqPerMonthQta());
        assertEquals(50, elig.reqPerSecQta());
        Thread.sleep(500);
        assertEquals(sub.subPlnId(), subs.findByDfId(df).block().subPlnId(), "the redelivered event changed nothing");
    }

    @Test
    void anEventFromBeforePlansWereChosenSubscribesToFree() throws Exception {
        String df = id("df");

        publish(Map.of("evtId", UUID.randomUUID().toString(), "occurredAt", "2026-08-30T10:00:00Z", "dfId", df,
                "name", "Old Fiduciary"));

        awaitSubscription(df);
        assertEquals(1000, eligibility.handle(df).block().reqPerMonthQta());
    }

    @Test
    void usageCountsUpAndIsReportedToTheEligibilityCheck() throws Exception {
        String df = id("df");
        publish(activated(df, "FREE"));
        awaitSubscription(df);

        usage.add(df, 1).block();
        usage.add(df, 1).block();
        assertTrue(reservations.reserve(df, 10).block().reserved());

        SubEligResult elig = eligibility.handle(df).block();
        assertEquals(12, elig.curPeriodUsg());
        assertEquals(DfSubSts.ACTIVE, elig.dfSubSts());
    }

    @Test
    void aBatchThatWouldOverspendTheMonthIsRefusedAndTakesNothing() throws Exception {
        String df = id("df");
        publish(activated(df, "FREE"));
        awaitSubscription(df);
        usage.add(df, 990).block();

        UsgReservationHandler.Reservation tooMany = reservations.reserve(df, 11).block();

        assertFalse(tooMany.reserved());
        assertEquals(10, tooMany.available());
        assertEquals(990, usage.currentPeriodUsage(df).block(), "the refused reservation was undone");
        UsgReservationHandler.Reservation exactly = reservations.reserve(df, 10).block();
        assertTrue(exactly.reserved());
        assertEquals(0, exactly.available());
    }

    @Test
    void aFiduciaryNeverActivatedHasNoSubscription() {
        DomRuleViolation missing = assertThrows(DomRuleViolation.class, () -> eligibility.handle(id("df")).block());
        assertEquals(404, missing.httpStatus());
    }

    private static Map<String, Object> activated(String df, String plan) {
        return Map.of("evtId", UUID.randomUUID().toString(), "occurredAt", "2026-11-03T10:00:00Z", "dfId", df,
                "name", "Fiduciary " + df, "subPlnName", plan);
    }

    private static void publish(Map<String, Object> evt) throws Exception {
        try (Producer<String> producer = pulsarClient.newProducer(Schema.STRING)
                .topic(PulsarTopics.evt("dfreg", "df-activated")).create()) {
            producer.send(JSON.writeValueAsString(evt));
        }
    }

    private static DfSub awaitSubscription(String df) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            DfSub sub = subs.findByDfId(df).block();
            if (sub != null) {
                return sub;
            }
            Thread.sleep(200);
        }
        fail("no subscription for " + df);
        return null;
    }

    private static String id(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
