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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.bil.dom.InvoiceCalc;
import com.techeazyhq.cm.bil.infra.R2dbcInvoiceRepoAdapter;
import com.techeazyhq.cm.testkit.PgIntegrationTestBase;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import reactor.core.publisher.Flux;

/**
 * Ending a fiduciary's billing when its departure begins, and resuming it when the departure is called off (ADR-109), against a real
 * PostgreSQL: the plan stretches the invoices are worked out from.
 */
class BillingEndAndResumeIT extends PgIntegrationTestBase {

    private static final String FREE = "00000000-0000-0000-0000-000000000001";
    private static final String STANDARD = "00000000-0000-0000-0000-000000000002";
    private static final Instant JAN = Instant.parse("2026-01-01T00:00:00Z");
    private static DatabaseClient db;
    private static R2dbcInvoiceRepoAdapter repo;

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
        repo = new R2dbcInvoiceRepoAdapter(db, null); // ending and resuming need no transaction of their own
    }

    /** A fiduciary on a plan since January, billed (one open stretch). */
    private static String billedFiduciary(String planId) {
        String df = UUID.randomUUID().toString();
        db.sql("insert into bil.df_sub (df_id, sub_pln_id, sts, cur_period_from, cur_period_to) values (:df, :pln, 'ACTIVE', :from, :to)")
                .bind("df", df).bind("pln", planId).bind("from", JAN).bind("to", JAN.plusSeconds(86_400 * 30L)).then().block();
        db.sql("insert into bil.plan_hst (df_id, sub_pln_id, from_at) values (:df, :pln, :from)")
                .bind("df", df).bind("pln", planId).bind("from", JAN).then().block();
        return df;
    }

    private static long open(String df) {
        return db.sql("select count(*) as n from bil.plan_hst where df_id = :df and to_at is null")
                .bind("df", df).map(r -> r.get("n", Long.class)).one().block();
    }

    private static long stretches(String df) {
        return db.sql("select count(*) as n from bil.plan_hst where df_id = :df")
                .bind("df", df).map(r -> r.get("n", Long.class)).one().block();
    }

    @Test
    void endingClosesTheOpenStretchAndResumingOpensANewOneOnThePlanItHas() {
        String df = billedFiduciary(FREE);
        Instant left = Instant.parse("2026-03-10T00:00:00Z");
        Instant back = Instant.parse("2026-03-20T00:00:00Z");

        repo.endBilling(df, left).block();
        assertEquals(0, open(df), "not billed once it is leaving");

        repo.resumeBilling(df, back).block();
        assertEquals(1, open(df), "billed again once the departure is called off");
        assertEquals(2, stretches(df));
    }

    @Test
    void theDaysBetweenAreNotBilled() {
        String df = billedFiduciary(FREE);
        Instant left = Instant.parse("2026-03-10T00:00:00Z");
        Instant back = Instant.parse("2026-03-20T00:00:00Z");
        repo.endBilling(df, left).block();
        repo.resumeBilling(df, back).block();

        List<InvoiceCalc.Segment> march = repo.segments(df, Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"))
                .collectList().block();

        assertEquals(2, march.size());
        assertEquals(left, march.get(0).to(), "the first stretch ends where the departure began");
        assertEquals(back, march.get(1).from(), "the second begins where it was called off");
        assertNull(march.get(1).to(), "and is open");
        assertTrue(march.get(0).to().isBefore(march.get(1).from()), "with a gap between that nobody is billed for");
    }

    @Test
    void resumingAfterAPlanChangeUsesTheCurrentPlan() {
        String df = billedFiduciary(FREE);
        repo.endBilling(df, Instant.parse("2026-03-10T00:00:00Z")).block();
        db.sql("update bil.df_sub set sub_pln_id = :pln where df_id = :df").bind("pln", STANDARD).bind("df", df).then().block();

        repo.resumeBilling(df, Instant.parse("2026-03-20T00:00:00Z")).block();

        String plan = db.sql("select sub_pln_id from bil.plan_hst where df_id = :df and to_at is null")
                .bind("df", df).map(r -> r.get("sub_pln_id", String.class)).one().block();
        assertEquals(STANDARD, plan);
    }

    @Test
    void endingTwiceAndResumingTwiceChangeNothingMore() {
        String df = billedFiduciary(FREE);
        Instant left = Instant.parse("2026-03-10T00:00:00Z");

        repo.endBilling(df, left).block();
        repo.endBilling(df, Instant.parse("2026-03-15T00:00:00Z")).block();
        assertEquals(0, open(df));
        repo.resumeBilling(df, Instant.parse("2026-03-20T00:00:00Z")).block();
        repo.resumeBilling(df, Instant.parse("2026-03-25T00:00:00Z")).block();

        assertEquals(1, open(df));
        assertEquals(2, stretches(df), "no extra stretch from the repeats");
    }

    @Test
    void resumingAFiduciaryThatIsStillBilledOpensNothing() {
        String df = billedFiduciary(FREE);

        repo.resumeBilling(df, Instant.parse("2026-03-20T00:00:00Z")).block();

        assertEquals(1, stretches(df));
    }

    @Test
    void resumingForAFiduciaryWithNoSubscriptionOpensNothingAndDoesNotFail() {
        String unknown = UUID.randomUUID().toString();

        repo.resumeBilling(unknown, Instant.now()).block();

        assertEquals(0, stretches(unknown));
    }

    @Test
    void replicasResumingTogetherOpenOneStretch() {
        String df = billedFiduciary(FREE);
        repo.endBilling(df, Instant.parse("2026-03-10T00:00:00Z")).block();

        Flux.range(0, 8).flatMap(i -> repo.resumeBilling(df, Instant.now())).collectList().block();

        assertEquals(1, open(df), "the unique index allows one open stretch");
        assertEquals(2, stretches(df));
    }
}
