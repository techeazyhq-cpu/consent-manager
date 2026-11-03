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

package com.techeazyhq.cm.dfreg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionInitialPosition;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.app.ActivateDfCmd;
import com.techeazyhq.cm.dfreg.app.ActivateDfCmdHandler;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmd;
import com.techeazyhq.cm.dfreg.app.RegisterDfCmdHandler;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfApiClientProvisionPort;
import com.techeazyhq.cm.dfreg.dom.DfMember;
import com.techeazyhq.cm.dfreg.dom.DfMemberDirectoryPort;
import com.techeazyhq.cm.dfreg.dom.DfRole;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.dom.NewDfMember;
import com.techeazyhq.cm.dfreg.infra.AesGcmSecretSealer;
import com.techeazyhq.cm.dfreg.infra.ObxRelay;
import com.techeazyhq.cm.dfreg.infra.PulsarDfEvtProducer;
import com.techeazyhq.cm.dfreg.infra.R2dbcDfRepoAdapter;
import com.techeazyhq.cm.evtkit.PulsarTopics;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;
import com.techeazyhq.cm.testkit.PulsarTestBroker;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Register, then activate, against a real PostgreSQL and Pulsar (#121): the fiduciary is stored ACTIVE with its
 * sealed API secret, and the outbox relay publishes {@code DfActivatedEvt}, which cm-bil-svc subscribes the
 * fiduciary from. Keycloak and cm-bil-svc's plan catalogue are stand-ins.
 */
@Testcontainers
class DfRegistrationFlowIT extends PgIntegrationTestBase {

    @Container
    static final PulsarContainer PULSAR = PulsarTestBroker.container();

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final List<NewDfMember> INVITED = new CopyOnWriteArrayList<>();

    private static DatabaseClient db;
    private static PulsarClient pulsarClient;
    private static RegisterDfCmdHandler register;
    private static ActivateDfCmdHandler activate;
    private static ObxRelay relay;

    @BeforeAll
    static void setUp() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("df_reg").defaultSchema("df_reg").locations("classpath:db/migration")
                .load().migrate();
        db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build()));
        PulsarTestBroker.createNamespaces(PULSAR, "dfreg");
        pulsarClient = PulsarClient.builder().serviceUrl(PULSAR.getPulsarBrokerUrl()).build();

        R2dbcDfRepoAdapter repo = new R2dbcDfRepoAdapter(db, JSON,
                new AesGcmSecretSealer(Base64.getEncoder().encodeToString(new byte[32])));
        register = new RegisterDfCmdHandler(repo, plan -> Mono.just(List.of("FREE", "STANDARD").contains(plan)));
        activate = new ActivateDfCmdHandler(repo, new StandInKeycloakClients(), new StandInMembers());
        relay = new ObxRelay(db, new PulsarDfEvtProducer(AdaptedReactivePulsarClientFactory.create(pulsarClient)));
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (pulsarClient != null) {
            pulsarClient.close();
        }
    }

    @Test
    void anActivatedFiduciaryIsStoredActiveAndDfActivatedEvtIsPublished() throws Exception {
        try (Consumer<String> consumer = pulsarClient.newConsumer(Schema.STRING)
                .topic(PulsarTopics.evt("dfreg", "df-activated"))
                .subscriptionName("it-bil")
                .subscriptionInitialPosition(SubscriptionInitialPosition.Earliest)
                .subscribe()) {

            Df registered = register.handle(new RegisterDfCmd("Acme Bank", "dpo@acme.test",
                    RegisterDfCmd.PrflFields.NONE, "STANDARD")).block();
            assertNotNull(registered);
            assertEquals(DfSts.PENDING, stored(registered.id()).sts());
            assertEquals(0L, outboxRows(registered.id()), "nothing to publish before activation");

            Df active = activate.handle(new ActivateDfCmd(registered.id())).block();
            assertNotNull(active);

            Df stored = stored(registered.id());
            assertEquals(DfSts.ACTIVE, stored.sts());
            assertNotNull(stored.activatedAt());
            assertEquals("secret-for-" + registered.id(), stored.cmDfApiClientSecret());
            assertTrue(INVITED.stream().anyMatch(m -> m.email().equals("dpo@acme.test") && m.role() == DfRole.OWNER),
                    "the contact is invited as the first Owner");

            relay.relayPendingEvents();
            Message<String> message = consumer.receive(30, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(message, "DfActivatedEvt reached the topic");
            JsonNode evt = JSON.readTree(message.getValue());
            assertEquals(registered.id(), evt.get("dfId").asText());
            assertEquals("Acme Bank", evt.get("name").asText());
            assertEquals("STANDARD", evt.get("subPlnName").asText());
            consumer.acknowledge(message);

            awaitPublished(registered.id());
        }
    }

    @Test
    void aPlanCmBilSvcDoesNotOfferIsRefusedAndNothingIsStored() {
        Long before = db.sql("select count(*) from df_reg.df").map((row, meta) -> row.get(0, Long.class)).one().block();
        RuntimeException refused = assertThrows(RuntimeException.class, () -> register.handle(
                new RegisterDfCmd("Beta Ltd", "dpo@beta.test", RegisterDfCmd.PrflFields.NONE, "GOLD")).block());
        assertTrue(refused.getMessage().contains("GOLD"));
        assertEquals(before, db.sql("select count(*) from df_reg.df").map((row, meta) -> row.get(0, Long.class)).one().block());
    }

    private static Df stored(String id) {
        return new R2dbcDfRepoAdapter(db, JSON, new AesGcmSecretSealer(Base64.getEncoder().encodeToString(new byte[32])))
                .findById(id).block();
    }

    private static long outboxRows(String dfId) {
        return db.sql("select count(*) from df_reg.outbox_evt where agg_id = :id and evt_type = 'DfActivatedEvt'")
                .bind("id", dfId).map((row, meta) -> row.get(0, Long.class)).one().block();
    }

    /** The relay marks the row once Pulsar has it; it runs off the caller's thread, so wait for it. */
    private static void awaitPublished(String dfId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            Object publishedAt = db.sql("select published_at from df_reg.outbox_evt where agg_id = :id and evt_type = 'DfActivatedEvt'")
                    .bind("id", dfId).map((row, meta) -> java.util.Optional.ofNullable(row.get(0)))
                    .one().block().orElse(null);
            if (publishedAt != null) {
                return;
            }
            Thread.sleep(100);
        }
        fail("the outbox row was never marked published");
    }

    private static final class StandInKeycloakClients implements DfApiClientProvisionPort {
        @Override
        public Mono<String> provisionClientSecret(String dfId) {
            return Mono.just("secret-for-" + dfId);
        }

        @Override
        public Mono<String> rotateClientSecret(String dfId) {
            return Mono.just("rotated-for-" + dfId);
        }
    }

    private static final class StandInMembers implements DfMemberDirectoryPort {
        @Override
        public Mono<DfMember> invite(String dfId, NewDfMember member) {
            INVITED.add(member);
            return Mono.just(new DfMember("m-" + INVITED.size(), dfId, member.email(), member.name(), member.role()));
        }

        @Override
        public Flux<DfMember> list(String dfId) {
            return Flux.empty();
        }

        @Override
        public Mono<DfMember> find(String dfId, String memberId) {
            return Mono.empty();
        }

        @Override
        public Mono<DfMember> changeRole(DfMember member, DfRole role) {
            return Mono.just(member);
        }

        @Override
        public Mono<Void> remove(DfMember member) {
            return Mono.empty();
        }
    }
}
