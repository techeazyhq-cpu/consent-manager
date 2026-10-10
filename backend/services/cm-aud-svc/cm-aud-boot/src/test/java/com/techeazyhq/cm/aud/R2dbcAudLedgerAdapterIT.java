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

import com.techeazyhq.cm.aud.app.AudLedgerService;
import com.techeazyhq.cm.aud.dom.AudChainVerifier;
import com.techeazyhq.cm.aud.dom.AudDraft;
import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudQuery;
import com.techeazyhq.cm.aud.infra.R2dbcAudLedgerAdapter;
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

import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ledger's SQL and its tamper resistance against a real PostgreSQL, migrated by the service's own Flyway scripts. */
@Testcontainers
class R2dbcAudLedgerAdapterIT extends PgIntegrationTestBase {

    private static DatabaseClient db;
    private static AudLedgerService ledger;

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
        ledger = new AudLedgerService(
                new R2dbcAudLedgerAdapter(db, TransactionalOperator.create(new R2dbcTransactionManager(cf))),
                Clock.systemUTC());
    }

    @BeforeEach
    void startEmpty() {
        sql("alter table aud.entry disable trigger entry_no_truncate");
        sql("truncate aud.entry_subject, aud.entry_payload, aud.entry cascade");
        sql("alter table aud.entry enable trigger entry_no_truncate");
        sql("update aud.chain_head set seq = 0, entry_hash = repeat('0', 64)");
    }

    private static void sql(String statement) {
        db.sql(statement).then().block();
    }

    private static AudDraft draft(String artefact, String dpId) {
        return new AudDraft(UUID.randomUUID(), "CnsGrantedEvt", Instant.now(), "req-" + artefact, artefact, "df-1", dpId,
                "{\"cnsArtId\":\"" + artefact + "\",\"dpRef\":{\"dpId\":\"" + dpId + "\"}}");
    }

    @Test
    void appendsLinkTheChainAndARedeliveredEventIsRecordedOnce() {
        AudDraft first = draft("art-1", "dp-1");
        AudEntry one = ledger.record(first).block();
        AudEntry two = ledger.record(draft("art-2", "dp-1")).block();
        AudEntry again = ledger.record(first).block();

        assertEquals(1, one.seq());
        assertEquals(one.entryHash(), two.prevHash());
        assertEquals(one, again);
        AudChainVerifier.Verdict verdict = ledger.verify().block();
        assertTrue(verdict.intact());
        assertEquals(2, verdict.headSeq());
        assertEquals(two.entryHash(), ledger.head().block().entryHash());
    }

    @Test
    void concurrentAppendsNeverForkTheChain() {
        Flux.range(0, 20)
                .parallel(8).runOn(Schedulers.parallel())
                .flatMap(n -> ledger.record(draft("art-" + n, "dp-1")))
                .sequential()
                .blockLast();

        AudChainVerifier.Verdict verdict = ledger.verify().block();
        assertTrue(verdict.intact(), verdict.reason());
        assertEquals(20, verdict.entriesChecked());
    }

    @Test
    void theDatabaseRefusesToChangeOrRemoveAnEntry() {
        ledger.record(draft("art-1", "dp-1")).block();

        assertThrows(RuntimeException.class, () -> sql("update aud.entry set evt_type = 'CnsDeniedEvt' where seq = 1"));
        assertThrows(RuntimeException.class, () -> sql("delete from aud.entry where seq = 1"));
        assertThrows(RuntimeException.class, () -> sql("truncate aud.entry cascade"));
    }

    @Test
    void tamperingThatGetsPastTheTriggerIsCaughtByVerification() {
        ledger.record(draft("art-1", "dp-1")).block();
        ledger.record(draft("art-2", "dp-1")).block();
        ledger.record(draft("art-3", "dp-1")).block();

        sql("alter table aud.entry disable trigger entry_append_only");
        sql("update aud.entry set evt_type = 'CnsDeniedEvt' where seq = 2");
        sql("alter table aud.entry enable trigger entry_append_only");

        AudChainVerifier.Verdict verdict = ledger.verify().block();
        assertFalse(verdict.intact());
        assertEquals(2L, verdict.firstBrokenSeq());
    }

    @Test
    void erasingAPrincipalsDataKeepsTheChainVerifiable() {
        ledger.record(draft("art-1", "dp-erased")).block();
        ledger.record(draft("art-2", "dp-kept")).block();

        sql("delete from aud.entry_payload where seq in (select seq from aud.entry_subject where dp_id = 'dp-erased')");
        sql("delete from aud.entry_subject where dp_id = 'dp-erased'");

        AudChainVerifier.Verdict verdict = ledger.verify().block();
        assertTrue(verdict.intact());
        assertEquals(1, verdict.payloadsErased());
        assertTrue(ledger.find(new AudQuery(null, null, null, "dp-erased", 10)).collectList().block().isEmpty());
    }

    @Test
    void entriesAreFoundByArtefactFiduciaryAndPrincipalNewestFirst() {
        ledger.record(draft("art-1", "dp-1")).block();
        ledger.record(draft("art-2", "dp-2")).block();
        ledger.record(draft("art-3", "dp-1")).block();

        List<AudEntry> forDp1 = ledger.find(new AudQuery(null, null, null, "dp-1", 10)).collectList().block();
        assertEquals(List.of("art-3", "art-1"), forDp1.stream().map(AudEntry::cnsArtId).toList());
        assertEquals(1, ledger.find(new AudQuery(null, "art-2", null, null, 10)).collectList().block().size());
        assertEquals(3, ledger.find(new AudQuery(null, null, "df-1", null, 10)).collectList().block().size());
        assertEquals(2, ledger.find(new AudQuery(null, null, null, null, 2)).collectList().block().size());
    }
}
