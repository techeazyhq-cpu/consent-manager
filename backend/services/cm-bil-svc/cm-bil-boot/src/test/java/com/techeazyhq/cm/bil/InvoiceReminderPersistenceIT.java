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
import com.techeazyhq.cm.bil.app.InvoiceReminderHandler;
import com.techeazyhq.cm.bil.dom.InvoiceReminderPort;
import com.techeazyhq.cm.bil.infra.R2dbcBilOutbox;
import com.techeazyhq.cm.bil.infra.R2dbcInvoiceReminderAdapter;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;

/**
 * Invoice reminders against a real PostgreSQL (ADR-105): the migration, which invoices are looked at, and the rule the primary key
 * enforces: replicas sweeping at the same moment raise one reminder per step between them, with one event and one audit entry.
 */
class InvoiceReminderPersistenceIT extends PgIntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static DatabaseClient db;
    private static R2dbcInvoiceReminderAdapter repo;
    private static InvoiceReminderHandler handler;

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
        repo = new R2dbcInvoiceReminderAdapter(db);
        R2dbcBilOutbox outbox = new R2dbcBilOutbox(db, JSON, null); // the relay to Pulsar isn't run here
        handler = new InvoiceReminderHandler(repo, outbox, AuditTrail.to("bil", outbox::enqueue));
    }

    /** One invoice row, as issued: kind, status and total are what the reminders look at. */
    private static String invoice(String kind, String sts, long total, Instant issuedAt) {
        String id = UUID.randomUUID().toString();
        db.sql("""
                        insert into bil.invoice (id, number, kind, df_id, period_from, period_to, currency, subtotal_minor, tax_label,
                                                 tax_bps, tax_minor, total_minor, sts, issued_at)
                        values (:id, :number, :kind, :df, :from, :to, 'INR', :total, 'Tax', 0, 0, :total, :sts, :issuedAt)
                        """)
                .bind("id", id).bind("number", "T-" + id.substring(0, 8)).bind("kind", kind).bind("df", UUID.randomUUID().toString())
                .bind("from", issuedAt.minusSeconds(86_400 * 31L)).bind("to", issuedAt.minusSeconds(86_400))
                .bind("total", total).bind("sts", sts).bind("issuedAt", issuedAt)
                .then().block();
        return id;
    }

    private static List<String> found(Instant before) {
        return repo.unpaidIssuedBefore(before).map(InvoiceReminderPort.Unpaid::invoiceId).collectList().block();
    }

    private static long count(String where, String id) {
        return db.sql("select count(*) as n from bil.outbox_evt where " + where)
                .bind("id", id).map(r -> r.get("n", Long.class)).one().block();
    }

    @Test
    void onlyAnIssuedInvoiceWithSomethingToPayIsLookedAt() {
        Instant longAgo = Instant.parse("2026-01-01T00:00:00Z");
        String unpaid = invoice("INVOICE", "ISSUED", 118_000, longAgo);
        String paid = invoice("INVOICE", "PAID", 118_000, longAgo);
        String credited = invoice("INVOICE", "CREDITED", 118_000, longAgo);
        String zero = invoice("INVOICE", "ISSUED", 0, longAgo);
        String note = invoice("CREDIT_NOTE", "ISSUED", -118_000, longAgo);
        String recent = invoice("INVOICE", "ISSUED", 118_000, Instant.parse("2026-12-01T00:00:00Z"));

        List<String> ids = found(Instant.parse("2026-06-01T00:00:00Z"));

        assertTrue(ids.contains(unpaid), "an issued invoice is looked at");
        assertEquals(false, ids.contains(paid), "a paid one is not");
        assertEquals(false, ids.contains(credited), "nor a credited one");
        assertEquals(false, ids.contains(zero), "nor one with nothing to pay");
        assertEquals(false, ids.contains(note), "nor a credit note");
        assertEquals(false, ids.contains(recent), "nor one issued after the cut-off");
    }

    @Test
    void anInvoiceThatIsPaidLaterIsNoLongerLookedAt() {
        String id = invoice("INVOICE", "ISSUED", 5_000, Instant.parse("2026-01-02T00:00:00Z"));
        assertTrue(found(Instant.parse("2026-06-01T00:00:00Z")).contains(id));

        // the one change an invoice allows (ADR-079)
        db.sql("update bil.invoice set sts = 'PAID', paid_at = now() where id = :id").bind("id", id).then().block();

        assertEquals(false, found(Instant.parse("2026-06-01T00:00:00Z")).contains(id));
    }

    @Test
    void replicasRaisingTheSameStepTogetherRaiseItOnce() {
        String id = invoice("INVOICE", "ISSUED", 118_000, Instant.parse("2026-01-03T00:00:00Z"));
        InvoiceReminderPort.Unpaid unpaid = new InvoiceReminderPort.Unpaid(id, "T-" + id.substring(0, 8), UUID.randomUUID().toString(),
                118_000, "INR", Instant.parse("2026-01-03T00:00:00Z"));
        Instant due = Instant.parse("2026-01-17T00:00:00Z");

        List<Boolean> raised = Flux.range(0, 8)
                .flatMap(i -> handler.raise(unpaid, due, 3, Instant.now()))
                .collectList().block();

        assertEquals(1, raised.stream().filter(Boolean::booleanValue).count(), raised.toString());
        assertEquals(1L, count("evt_type = 'InvoiceReminderEvt' and agg_id = :id || '/3'", id));
        assertEquals(1L, count("evt_type = 'PlatformActionEvt' and payload_json like '%' || :id || '%'", unpaid.dfId()));
        String payload = db.sql("select payload_json from bil.outbox_evt where evt_type = 'InvoiceReminderEvt' and agg_id = :id || '/3'")
                .bind("id", id).map(r -> r.get("payload_json", String.class)).one().block();
        assertTrue(payload.contains("\"stepDays\":3") && payload.contains("\"totalMinor\":118000") && payload.contains("\"currency\":\"INR\""), payload);
        assertTrue(!payload.contains("@"), "no address in the event: " + payload);
    }

    @Test
    void eachStepOfAnInvoiceIsItsOwnReminder() {
        String id = invoice("INVOICE", "ISSUED", 118_000, Instant.parse("2026-01-04T00:00:00Z"));
        Instant now = Instant.now();

        assertTrue(repo.addIfNew(id, 3, now).block());
        assertTrue(repo.addIfNew(id, 10, now).block());
        assertEquals(false, repo.addIfNew(id, 3, now).block());
    }
}
