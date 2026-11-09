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

package com.techeazyhq.cm.bil.infra;

import java.time.Instant;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.bil.dom.InvoiceReminderPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class R2dbcInvoiceReminderAdapter implements InvoiceReminderPort {

    private final DatabaseClient db;

    public R2dbcInvoiceReminderAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Flux<Unpaid> unpaidIssuedBefore(Instant issuedBefore) {
        return db.sql("""
                        select id, number, df_id, total_minor, currency, issued_at from bil.invoice
                        where kind = 'INVOICE' and sts = 'ISSUED' and total_minor > 0 and issued_at <= :before
                        order by issued_at
                        """)
                .bind("before", issuedBefore)
                .map((row, meta) -> new Unpaid(row.get("id", String.class), row.get("number", String.class), row.get("df_id", String.class),
                        row.get("total_minor", Long.class), row.get("currency", String.class), row.get("issued_at", Instant.class)))
                .all();
    }

    /** The primary key {@code (invoice_id, step_days)} makes "already raised" a no-op insert. */
    @Override
    public Mono<Boolean> addIfNew(String invoiceId, int stepDays, Instant raisedAt) {
        return db.sql("""
                        insert into bil.invoice_reminder (invoice_id, step_days, raised_at) values (:id, :step, :at)
                        on conflict (invoice_id, step_days) do nothing
                        """)
                .bind("id", invoiceId)
                .bind("step", stepDays)
                .bind("at", raisedAt)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }
}
