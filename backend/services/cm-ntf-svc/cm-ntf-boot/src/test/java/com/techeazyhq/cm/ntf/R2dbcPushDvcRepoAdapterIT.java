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

package com.techeazyhq.cm.ntf;

import com.techeazyhq.cm.ntf.dom.PushDvc;
import com.techeazyhq.cm.ntf.dom.PushPltf;
import com.techeazyhq.cm.ntf.infra.R2dbcPushDvcRepoAdapter;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The push-device SQL against a real Postgres, migrated by the service's own Flyway scripts. */
@Testcontainers
class R2dbcPushDvcRepoAdapterIT extends PgIntegrationTestBase {

    private static DatabaseClient db;
    private static R2dbcPushDvcRepoAdapter repo;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("ntf").defaultSchema("ntf").locations("classpath:db/migration")
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
        repo = new R2dbcPushDvcRepoAdapter(db, TransactionalOperator.create(new R2dbcTransactionManager(cf)));
    }

    @BeforeEach
    void clean() {
        db.sql("delete from ntf.push_dvc").then().block();
    }

    @Test
    void registeringTheSameTokenAgainRefreshesTheSameDevice() {
        PushDvc first = repo.upsert("dp-1", "tok-1", PushPltf.WEB).block();
        PushDvc again = repo.upsert("dp-1", "tok-1", PushPltf.WEB).block();

        assertEquals(first.id(), again.id());
        assertEquals(first.registeredAt(), again.registeredAt());
        assertTrue(!again.lastSeenAt().isBefore(first.lastSeenAt()));
        assertEquals(1, repo.findByDpId("dp-1").collectList().block().size());
    }

    @Test
    void aTokenRegisteredByAnotherDpMovesToThemUnderANewId() {
        PushDvc ann = repo.upsert("dp-ann", "shared-browser", PushPltf.WEB).block();
        PushDvc bob = repo.upsert("dp-bob", "shared-browser", PushPltf.WEB).block();

        assertNotEquals(ann.id(), bob.id());
        assertTrue(repo.findByDpId("dp-ann").collectList().block().isEmpty());
        assertEquals(List.of("shared-browser"), repo.findByDpId("dp-bob").map(PushDvc::token).collectList().block());
    }

    @Test
    void onlyTheMostRecentDevicesPerDpAreKept() {
        IntStream.range(0, PushDvc.MAX_PER_DP + 2).forEach(i -> repo.upsert("dp-1", "tok-" + i, PushPltf.WEB).block());

        List<String> tokens = repo.findByDpId("dp-1").map(PushDvc::token).collectList().block();
        assertEquals(PushDvc.MAX_PER_DP, tokens.size());
        assertFalse(tokens.contains("tok-0"));
        assertFalse(tokens.contains("tok-1"));
        assertEquals("tok-" + (PushDvc.MAX_PER_DP + 1), tokens.get(0));
    }

    @Test
    void aDpCanOnlyRemoveTheirOwnDevice() {
        PushDvc ann = repo.upsert("dp-ann", "tok-ann", PushPltf.WEB).block();

        assertFalse(repo.delete("dp-bob", ann.id()).block());
        assertFalse(repo.delete("dp-ann", "not-a-uuid").block());
        assertTrue(repo.delete("dp-ann", ann.id()).block());
        assertTrue(repo.findByDpId("dp-ann").collectList().block().isEmpty());
    }
}
