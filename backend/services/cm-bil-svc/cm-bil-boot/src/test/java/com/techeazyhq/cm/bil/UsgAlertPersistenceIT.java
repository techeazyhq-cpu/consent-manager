package com.techeazyhq.cm.bil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techeazyhq.cm.audkit.AuditTrail;
import com.techeazyhq.cm.bil.app.UsgAlertHandler;
import com.techeazyhq.cm.bil.dom.UsgAlert;
import com.techeazyhq.cm.bil.infra.R2dbcBilOutbox;
import com.techeazyhq.cm.bil.infra.R2dbcUsgAlertRepoAdapter;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;

/**
 * Usage alerts against a real PostgreSQL (ADR-060): the migration, and the rule the primary key enforces. Replicas
 * sweeping at the same moment raise one alert between them, with one event for cm-df-reg-svc and one audit entry.
 */
class UsgAlertPersistenceIT extends PgIntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static DatabaseClient db;
    private static R2dbcUsgAlertRepoAdapter repo;
    private static UsgAlertHandler handler;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("bil").defaultSchema("bil").locations("classpath:db/migration")
                .load().migrate();
        db = DatabaseClient.create(ConnectionFactories.get(ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.DRIVER, "postgresql")
                .option(ConnectionFactoryOptions.HOST, POSTGRES.getHost())
                .option(ConnectionFactoryOptions.PORT, POSTGRES.getFirstMappedPort())
                .option(ConnectionFactoryOptions.DATABASE, POSTGRES.getDatabaseName())
                .option(ConnectionFactoryOptions.USER, POSTGRES.getUsername())
                .option(ConnectionFactoryOptions.PASSWORD, POSTGRES.getPassword())
                .build()));
        repo = new R2dbcUsgAlertRepoAdapter(db);
        R2dbcBilOutbox outbox = new R2dbcBilOutbox(db, JSON, null); // the relay to Pulsar isn't run here
        handler = new UsgAlertHandler(repo, outbox, AuditTrail.to("bil", outbox::enqueue));
    }

    private static UsgAlert alert(String dfId, int threshold) {
        return new UsgAlert(dfId, "2026-11", threshold, threshold * 10L, 1_000, "FREE", Instant.now());
    }

    private static long count(String where, String id) {
        return db.sql("select count(*) as n from bil.outbox_evt where " + where)
                .bind("id", id).map(r -> r.get("n", Long.class)).one().block();
    }

    @Test
    void replicasRaisingTheSameAlertTogetherRaiseItOnce() {
        String df = UUID.randomUUID().toString();
        Instant resets = Instant.parse("2026-12-01T00:00:00Z");

        List<Boolean> raised = Flux.range(0, 8)
                .flatMap(i -> handler.raise(alert(df, 80), resets))
                .collectList().block();

        assertEquals(1, raised.stream().filter(Boolean::booleanValue).count(), raised.toString());
        assertEquals(1L, count("evt_type = 'UsgThresholdEvt' and agg_id like :id || '/%'", df));
        assertEquals(1L, count("evt_type = 'PlatformActionEvt' and payload_json like '%' || :id || '%'", df));
        String payload = db.sql("select payload_json from bil.outbox_evt where evt_type = 'UsgThresholdEvt' and agg_id like :id || '/%'")
                .bind("id", df).map(r -> r.get("payload_json", String.class)).one().block();
        assertTrue(payload.contains("\"threshold\":80") && payload.contains("\"planName\":\"FREE\""), payload);
        assertTrue(payload.contains("\"resetsAt\""), payload);
    }

    @Test
    void eachThresholdAndMonthIsItsOwnAlert() {
        String df = UUID.randomUUID().toString();
        Instant now = Instant.now();

        assertTrue(repo.addIfNew(alert(df, 80)).block());
        assertTrue(repo.addIfNew(alert(df, 100)).block());
        assertTrue(repo.addIfNew(new UsgAlert(df, "2026-12", 80, 800, 1_000, "FREE", now)).block());
        assertEquals(false, repo.addIfNew(alert(df, 80)).block());

        assertEquals(3, repo.findByDf(df, 10).count().block());
        assertEquals(2, repo.findByDf(df, 10).filter(a -> a.period().equals("2026-11")).count().block());
        assertTrue(repo.findByPeriod("2026-12").any(a -> a.dfId().equals(df)).block());
        assertEquals(1, repo.findByDf(df, 1).count().block());
    }
}
