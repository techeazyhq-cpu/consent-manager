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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.r2dbc.core.DatabaseClient;

import com.techeazyhq.cm.bil.dom.Invoice;
import com.techeazyhq.cm.bil.dom.InvoiceCalc;
import com.techeazyhq.cm.bil.dom.InvoiceRepoPort;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** {@link InvoiceRepoPort} adapter (ADR-079). */
public class R2dbcInvoiceRepoAdapter implements InvoiceRepoPort {

    private final DatabaseClient db;
    private final org.springframework.transaction.reactive.TransactionalOperator tx;

    public R2dbcInvoiceRepoAdapter(DatabaseClient db, org.springframework.transaction.reactive.TransactionalOperator tx) {
        this.db = db;
        this.tx = tx;
    }

    @Override
    public Flux<PlanPrice> prices() {
        return db.sql("select p.name, x.monthly_minor from bil.sub_pln p join bil.plan_price x on x.sub_pln_id = p.id order by x.monthly_minor, p.name")
                .map((row, meta) -> new PlanPrice(row.get("name", String.class), row.get("monthly_minor", Long.class))).all();
    }

    @Override
    public Mono<PlanPrice> setPrice(String planName, long monthlyMinor, Instant now) {
        return db.sql("""
                        insert into bil.plan_price (sub_pln_id, monthly_minor, updated_at)
                        select id, :price, :now from bil.sub_pln where name = :name
                        on conflict (sub_pln_id) do update set monthly_minor = excluded.monthly_minor, updated_at = excluded.updated_at
                        returning monthly_minor
                        """)
                .bind("price", monthlyMinor).bind("now", now).bind("name", planName)
                .map((row, meta) -> new PlanPrice(planName, row.get("monthly_minor", Long.class))).one();
    }

    @Override
    public Flux<String> dfsWithPlans(Instant from, Instant to) {
        return db.sql("select distinct df_id from bil.plan_hst where from_at < :to and (to_at is null or to_at > :from) order by df_id")
                .bind("from", from).bind("to", to).map((row, meta) -> row.get("df_id", String.class)).all();
    }

    @Override
    public Flux<InvoiceCalc.Segment> segments(String dfId, Instant from, Instant to) {
        return db.sql("""
                        select p.name, x.monthly_minor, h.from_at, h.to_at
                        from bil.plan_hst h join bil.sub_pln p on p.id = h.sub_pln_id join bil.plan_price x on x.sub_pln_id = p.id
                        where h.df_id = :dfId and h.from_at < :to and (h.to_at is null or h.to_at > :from)
                        order by h.from_at
                        """)
                .bind("dfId", dfId).bind("from", from).bind("to", to)
                .map((row, meta) -> new InvoiceCalc.Segment(row.get("name", String.class), row.get("monthly_minor", Long.class),
                        row.get("from_at", Instant.class), row.get("to_at", Instant.class))).all();
    }

    @Override
    public Mono<Void> endBilling(String dfId, Instant at) {
        return db.sql("update bil.plan_hst set to_at = :at where df_id = :dfId and to_at is null")
                .bind("at", at).bind("dfId", dfId).fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Invoice> issue(Invoice d) {
        String prefix = d.kind() == Invoice.Kind.INVOICE ? "INV" : "CN";
        int year = d.issuedAt().atZone(ZoneOffset.UTC).getYear();
        String lockKey = d.kind() == Invoice.Kind.INVOICE ? "inv:" + d.dfId() + ":" + d.periodFrom() : "cn:" + d.creditsInvoiceId();
        // Numbers have no gaps: whoever issues the same invoice at the same moment waits for the lock, finds it there, and takes
        // no number. The lock, the check, the number and the insert are one transaction.
        return tx.transactional(db.sql("select pg_advisory_xact_lock(hashtext(:key))").bind("key", lockKey).fetch().rowsUpdated()
                .then(db.sql(d.kind() == Invoice.Kind.INVOICE
                                ? "select id from bil.invoice where kind = 'INVOICE' and df_id = :a and period_from = :b"
                                : "select id from bil.invoice where credits_invoice_id = :a and cast(:b as timestamptz) is not null")
                        .bind("a", d.kind() == Invoice.Kind.INVOICE ? d.dfId() : d.creditsInvoiceId())
                        .bind("b", d.periodFrom()).map((row, meta) -> row.get("id", String.class)).first().hasElement())
                .flatMap(exists -> exists ? Mono.<Invoice>empty() : insert(d, prefix, year)));
    }

    private Mono<Invoice> insert(Invoice d, String prefix, int year) {
        return db.sql("""
                        with n as (
                            insert into bil.invoice_seq (prefix, yr, last_no) values (:prefix, :yr, 1)
                            on conflict (prefix, yr) do update set last_no = bil.invoice_seq.last_no + 1
                            returning last_no)
                        insert into bil.invoice (id, number, kind, df_id, period_from, period_to, currency, subtotal_minor, tax_label,
                                                 tax_bps, tax_minor, total_minor, sts, issued_at, credits_invoice_id, reason)
                        select :id, :prefix || '-' || :yr || '-' || lpad(n.last_no::text, 6, '0'), :kind, :dfId, :from, :to, :cur,
                               :sub, :taxLabel, :bps, :tax, :total, :sts, :issuedAt, nullif(cast(:credits as varchar), ''), nullif(cast(:reason as varchar), '')
                        from n
                        on conflict do nothing
                        returning number
                        """)
                .bind("prefix", prefix).bind("yr", year).bind("id", d.id()).bind("kind", d.kind().name()).bind("dfId", d.dfId())
                .bind("from", d.periodFrom()).bind("to", d.periodTo()).bind("cur", d.currency()).bind("sub", d.subtotalMinor())
                .bind("taxLabel", d.taxLabel()).bind("bps", d.taxBps()).bind("tax", d.taxMinor()).bind("total", d.totalMinor())
                .bind("sts", d.sts().name()).bind("issuedAt", d.issuedAt())
                .bind("credits", d.creditsInvoiceId() == null ? "" : d.creditsInvoiceId())
                .bind("reason", d.reason() == null ? "" : d.reason())
                .map((row, meta) -> row.get("number", String.class)).one()
                .flatMap(number -> insertLines(d.id(), d.lines()).then(find(d.id())));
    }

    private Mono<Void> insertLines(String invoiceId, List<Invoice.Line> lines) {
        return Flux.fromIterable(lines).concatMap(l -> db.sql("""
                        insert into bil.invoice_line (invoice_id, line_no, description, plan_name, from_at, to_at, qty_days, amount_minor)
                        values (:id, :no, :desc, :plan, :from, :to, :qty, :amount)
                        """)
                .bind("id", invoiceId).bind("no", l.lineNo()).bind("desc", l.description()).bind("plan", l.planName())
                .bind("from", l.from()).bind("to", l.to()).bind("qty", l.qtyDays()).bind("amount", l.amountMinor())
                .fetch().rowsUpdated()).then();
    }

    @Override
    public Mono<Invoice> find(String id) {
        return db.sql("select * from bil.invoice where id = :id").bind("id", id).map((row, meta) -> header(row)).one()
                .flatMap(this::withLines);
    }

    @Override
    public Flux<Invoice> list(String dfId, Instant periodFrom) {
        return db.sql("""
                        select * from bil.invoice
                        where (:dfId = '' or df_id = :dfId) and (:from = to_timestamp(0) or period_from = :from)
                        order by issued_at desc, number desc limit 1000
                        """)
                .bind("dfId", dfId == null ? "" : dfId).bind("from", periodFrom == null ? Instant.EPOCH : periodFrom)
                .map((row, meta) -> header(row)).all().concatMap(this::withLines);
    }

    @Override
    public Mono<Invoice> markPaid(String id, Instant at, String note) {
        return db.sql("update bil.invoice set sts = 'PAID', paid_at = :at, paid_note = :note where id = :id and sts = 'ISSUED' returning id")
                .bind("at", at).bind("note", note).bind("id", id).map((row, meta) -> row.get("id", String.class)).one()
                .flatMap(this::find);
    }

    @Override
    public Mono<Invoice> markCredited(String id) {
        return db.sql("update bil.invoice set sts = 'CREDITED' where id = :id and sts in ('ISSUED', 'PAID') returning id")
                .bind("id", id).map((row, meta) -> row.get("id", String.class)).one().flatMap(this::find);
    }

    private Mono<Invoice> withLines(Invoice h) {
        return db.sql("select * from bil.invoice_line where invoice_id = :id order by line_no").bind("id", h.id())
                .map((row, meta) -> new Invoice.Line(row.get("line_no", Integer.class), row.get("description", String.class),
                        row.get("plan_name", String.class), row.get("from_at", Instant.class), row.get("to_at", Instant.class),
                        row.get("qty_days", BigDecimal.class), row.get("amount_minor", Long.class)))
                .all().collectList().map(lines -> new Invoice(h.id(), h.number(), h.kind(), h.dfId(), h.periodFrom(), h.periodTo(),
                        h.currency(), h.subtotalMinor(), h.taxLabel(), h.taxBps(), h.taxMinor(), h.totalMinor(), h.sts(), h.issuedAt(),
                        h.paidAt(), h.paidNote(), h.creditsInvoiceId(), h.reason(), lines));
    }

    private static Invoice header(Readable row) {
        String credits = row.get("credits_invoice_id", String.class);
        String reason = row.get("reason", String.class);
        return new Invoice(row.get("id", String.class), row.get("number", String.class),
                Invoice.Kind.valueOf(row.get("kind", String.class)), row.get("df_id", String.class),
                row.get("period_from", Instant.class), row.get("period_to", Instant.class), row.get("currency", String.class),
                row.get("subtotal_minor", Long.class), row.get("tax_label", String.class), row.get("tax_bps", Integer.class),
                row.get("tax_minor", Long.class), row.get("total_minor", Long.class), Invoice.Status.valueOf(row.get("sts", String.class)),
                row.get("issued_at", Instant.class), row.get("paid_at", Instant.class), row.get("paid_note", String.class),
                credits == null || credits.isEmpty() ? null : credits, reason == null || reason.isEmpty() ? null : reason, List.of());
    }
}
