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

import com.techeazyhq.cm.aud.app.AudAnchoringService;
import com.techeazyhq.cm.aud.app.AudLedgerService;
import com.techeazyhq.cm.aud.dom.AudAnchor;
import com.techeazyhq.cm.aud.dom.AudChain;
import com.techeazyhq.cm.aud.dom.AudCheckpointVerifier;
import com.techeazyhq.cm.aud.dom.AudDraft;
import com.techeazyhq.cm.aud.dom.AudEntry;
import com.techeazyhq.cm.aud.dom.AudMerkle;
import com.techeazyhq.cm.aud.dom.AudRecord;
import com.techeazyhq.cm.aud.dom.TimestampAuthorityPort;
import com.techeazyhq.cm.aud.infra.R2dbcAudCheckpointAdapter;
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

import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checkpoints and anchoring against a real PostgreSQL (ADR-015). The timestamp authority is a stand-in whose "token"
 * is the hash it was given; the real RFC 3161 exchange is covered by {@code Rfc3161TimestampAuthorityTest}.
 */
@Testcontainers
class AudAnchoringIT extends PgIntegrationTestBase {

    private static final AtomicBoolean AUTHORITY_UP = new AtomicBoolean(true);

    private static final TimestampAuthorityPort STAND_IN_AUTHORITY = new TimestampAuthorityPort() {
        @Override
        public Mono<AudAnchor> timestamp(long checkpointId, String sha256Hex) {
            return AUTHORITY_UP.get()
                    ? Mono.just(new AudAnchor(checkpointId, "test-tsa", Instant.now(), sha256Hex.getBytes(StandardCharsets.US_ASCII)))
                    : Mono.error(new IllegalStateException("authority unreachable"));
        }

        @Override
        public String verifiedImprint(byte[] token) {
            return new String(token, StandardCharsets.US_ASCII);
        }
    };

    private static DatabaseClient db;
    private static AudLedgerService ledger;
    private static AudAnchoringService anchoring;

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
        R2dbcAudLedgerAdapter ledgerAdapter = new R2dbcAudLedgerAdapter(db, TransactionalOperator.create(new R2dbcTransactionManager(cf)));
        ledger = new AudLedgerService(ledgerAdapter, Clock.systemUTC());
        anchoring = new AudAnchoringService(ledgerAdapter, new R2dbcAudCheckpointAdapter(db), STAND_IN_AUTHORITY, Clock.systemUTC());
    }

    @BeforeEach
    void startEmpty() {
        AUTHORITY_UP.set(true);
        for (String table : List.of("checkpoint_anchor", "checkpoint", "entry")) {
            sql("alter table aud." + table + " disable trigger all");
        }
        sql("delete from aud.checkpoint_anchor");
        sql("delete from aud.checkpoint");
        sql("delete from aud.entry_subject");
        sql("delete from aud.entry_payload");
        sql("delete from aud.entry");
        for (String table : List.of("checkpoint_anchor", "checkpoint", "entry")) {
            sql("alter table aud." + table + " enable trigger all");
        }
        sql("update aud.chain_head set seq = 0, entry_hash = repeat('0', 64)");
    }

    private static void sql(String statement) {
        db.sql(statement).then().block();
    }

    private static void record(int count) {
        for (int n = 0; n < count; n++) {
            String art = UUID.randomUUID().toString().substring(0, 8);
            ledger.record(new AudDraft(UUID.randomUUID(), "CnsGrantedEvt", Instant.now(), "req-" + art, "art-" + art, "df-1",
                    "dp-1", "{\"cnsArtId\":\"art-" + art + "\"}")).block();
        }
    }

    @Test
    void eachRunCheckpointsOnlyTheNewEntriesAndAnchorsThem() {
        record(5);
        anchoring.checkpointAndAnchor().block();
        anchoring.checkpointAndAnchor().block();
        record(3);
        anchoring.checkpointAndAnchor().block();

        var checkpoints = anchoring.list().collectList().block();
        assertEquals(2, checkpoints.size());
        assertEquals(1, checkpoints.get(0).checkpoint().fromSeq());
        assertEquals(5, checkpoints.get(0).checkpoint().toSeq());
        assertEquals(6, checkpoints.get(1).checkpoint().fromSeq());
        assertEquals(8, checkpoints.get(1).checkpoint().toSeq());
        assertTrue(checkpoints.stream().allMatch(c -> c.anchor() != null));
        AudCheckpointVerifier.Verdict verdict = anchoring.verify().block();
        assertTrue(verdict.intact(), verdict.reason());
        assertEquals(2, verdict.anchored());
        assertEquals(8, verdict.anchoredUpToSeq());
    }

    @Test
    void aCheckpointWaitsForTheAuthorityAndIsAnchoredWhenItIsBack() {
        record(2);
        AUTHORITY_UP.set(false);
        anchoring.checkpointAndAnchor().block();
        assertNull(anchoring.list().blockFirst().anchor());

        AUTHORITY_UP.set(true);
        anchoring.checkpointAndAnchor().block();

        assertNotNull(anchoring.list().blockFirst().anchor());
        assertEquals(1, anchoring.list().count().block());
    }

    @Test
    void anEntrysProofLeadsToTheAnchoredRoot() {
        record(7);
        anchoring.checkpointAndAnchor().block();

        AudAnchoringService.InclusionProof proof = anchoring.proof(4).block();

        assertEquals(4, proof.entry().seq());
        assertTrue(AudMerkle.verify(proof.entry().entryHash(), proof.path(), proof.checkpoint().checkpoint().merkleRoot()));
        assertEquals(proof.checkpoint().checkpoint().checkpointHash(),
                new String(proof.checkpoint().anchor().token(), StandardCharsets.US_ASCII));
        assertNull(anchoring.proof(8).block());
    }

    @Test
    void checkpointsAndAnchorsCannotBeChangedOrRemoved() {
        record(2);
        anchoring.checkpointAndAnchor().block();

        assertThrows(RuntimeException.class, () -> sql("update aud.checkpoint set merkle_root = repeat('1', 64)"));
        assertThrows(RuntimeException.class, () -> sql("delete from aud.checkpoint_anchor"));
    }

    @Test
    void anOperatorWhoRewritesTheWholeChainConsistentlyIsCaughtByTheAnchoredCheckpoint() {
        record(5);
        anchoring.checkpointAndAnchor().block();

        rewriteHistoryConsistentlyFrom(2);

        assertTrue(ledger.verify().block().intact(), "the rewritten chain is internally consistent");
        AudCheckpointVerifier.Verdict verdict = anchoring.verify().block();
        assertFalse(verdict.intact());
        assertEquals(1L, verdict.firstBrokenId());
        assertTrue(verdict.reason().contains("Merkle root"));
    }

    /**
     * What an operator with full database rights could do: turn the triggers off, change entry {@code fromSeq}, and
     * recompute every later hash and the head so the chain verifies on its own again.
     */
    private static void rewriteHistoryConsistentlyFrom(long fromSeq) {
        List<AudRecord> records = new R2dbcAudLedgerAdapter(db, null).records().collectList().block();
        sql("alter table aud.entry disable trigger all");
        String prevHash = records.get((int) fromSeq - 2).entry().entryHash();
        for (AudRecord rec : records.subList((int) fromSeq - 1, records.size())) {
            AudEntry e = rec.entry();
            String type = e.seq() == fromSeq ? "CnsDeniedEvt" : e.evtType();
            AudEntry rewritten = AudChain.link(e.seq() - 1, prevHash, new AudDraft(e.evtId(), type, e.occurredAt(),
                    e.cnsReqId(), e.cnsArtId(), e.dfId(), null, rec.payloadJson()), e.recordedAt());
            db.sql("update aud.entry set evt_type = :type, prev_hash = :prev, entry_hash = :hash where seq = :seq")
                    .bind("type", type).bind("prev", prevHash).bind("hash", rewritten.entryHash()).bind("seq", e.seq())
                    .then().block();
            prevHash = rewritten.entryHash();
        }
        db.sql("update aud.chain_head set entry_hash = :hash").bind("hash", prevHash).then().block();
        sql("alter table aud.entry enable trigger all");
    }
}
