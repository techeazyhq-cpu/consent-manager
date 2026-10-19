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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Base64;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.dfreg.dom.Df;
import com.techeazyhq.cm.dfreg.dom.DfFactory;
import com.techeazyhq.cm.dfreg.dom.DfPrfl;
import com.techeazyhq.cm.dfreg.dom.DfSts;
import com.techeazyhq.cm.dfreg.infra.AesGcmSecretSealer;
import com.techeazyhq.cm.dfreg.infra.R2dbcDfRepoAdapter;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;

/** A fiduciary's API client secret is sealed at rest, and stored plain ones are sealed at start-up (#220). */
@Testcontainers
class R2dbcDfRepoAdapterIT extends PgIntegrationTestBase {

    private static DatabaseClient db;
    private static R2dbcDfRepoAdapter repo;

    @BeforeAll
    static void migrate() {
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
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        repo = new R2dbcDfRepoAdapter(db, new ObjectMapper(), new AesGcmSecretSealer(key));
    }

    @BeforeEach
    void clean() {
        db.sql("delete from df_reg.df").then().block();
    }

    @Test
    void theSecretIsSealedInTheTableAndOpenedOnRead() {
        repo.save(df("df-1", "client-secret-1")).block();

        String stored = storedSecret("df-1");
        assertTrue(stored.startsWith("v1:"), "stored sealed");
        assertTrue(!stored.contains("client-secret-1"));
        assertEquals("client-secret-1", repo.findById("df-1").block().cmDfApiClientSecret());
    }

    @Test
    void plainSecretsFromBeforeAreReadAndThenSealedOnce() {
        repo.save(df("df-old", "old-secret")).block();
        db.sql("update df_reg.df set cm_df_api_client_secret = 'old-secret' where id = 'df-old'").then().block();

        assertEquals("old-secret", repo.findById("df-old").block().cmDfApiClientSecret());
        assertEquals(1L, repo.sealPlainTextSecrets().block());
        assertNotEquals("old-secret", storedSecret("df-old"));
        assertEquals("old-secret", repo.findById("df-old").block().cmDfApiClientSecret());
        assertEquals(0L, repo.sealPlainTextSecrets().block(), "nothing left to seal");
    }

    @Test
    void savingAgainDoesNotSealTwice() {
        repo.save(df("df-2", "secret-2")).block();
        Df read = repo.findById("df-2").block();
        repo.save(read).block();
        assertEquals("secret-2", repo.findById("df-2").block().cmDfApiClientSecret());
    }

    private static Df df(String id, String secret) {
        return DfFactory.rehydrate(id, "Fiduciary " + id, id + "@example.test", DfPrfl.EMPTY, "FREE", DfSts.ACTIVE,
                Instant.parse("2026-10-19T10:00:00Z"), secret, Instant.parse("2026-10-19T10:01:00Z"));
    }

    private static String storedSecret(String id) {
        return db.sql("select cm_df_api_client_secret from df_reg.df where id = :id").bind("id", id)
                .map((row, meta) -> row.get(0, String.class)).one().block();
    }
}
