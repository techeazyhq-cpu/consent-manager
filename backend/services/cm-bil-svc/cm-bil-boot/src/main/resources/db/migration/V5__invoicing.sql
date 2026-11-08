-- Copyright 2026 Vasantha Kumar
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
-- @author Vasantha Kumar <vasantha.kumar@hotmail.com>

-- Invoices (ADR-079): a monthly price per plan, the history of which plan a fiduciary was on when (for proration), and
-- invoices and credit notes that are never changed once issued except to say they were paid or credited.

create table bil.plan_price (
    sub_pln_id    varchar(36) primary key references bil.sub_pln (id),
    monthly_minor bigint not null check (monthly_minor >= 0),
    updated_at    timestamptz not null
);
insert into bil.plan_price (sub_pln_id, monthly_minor, updated_at) select id, 0, now() from bil.sub_pln;

create table bil.plan_hst (
    id         bigserial primary key,
    df_id      varchar(36) not null,
    sub_pln_id varchar(36) not null references bil.sub_pln (id),
    from_at    timestamptz not null,
    to_at      timestamptz
);
create index idx_plan_hst_df on bil.plan_hst (df_id, from_at);
create unique index uq_plan_hst_open on bil.plan_hst (df_id) where to_at is null;
-- what is known of the past is the plan a subscription has now, from when it began
insert into bil.plan_hst (df_id, sub_pln_id, from_at) select df_id, sub_pln_id, cur_period_from from bil.df_sub;

create table bil.invoice_seq (
    prefix  varchar(4) not null,
    yr      int not null,
    last_no int not null,
    primary key (prefix, yr)
);

create table bil.invoice (
    id                 varchar(36) primary key,
    number             varchar(32) not null unique,
    kind               varchar(12) not null,  -- INVOICE | CREDIT_NOTE
    df_id              varchar(36) not null,
    period_from        timestamptz not null,
    period_to          timestamptz not null,
    currency           varchar(3) not null,
    subtotal_minor     bigint not null,
    tax_label          varchar(16) not null,
    tax_bps            int not null,
    tax_minor          bigint not null,
    total_minor        bigint not null,
    sts                varchar(12) not null,  -- ISSUED | PAID | CREDITED
    issued_at          timestamptz not null,
    paid_at            timestamptz,
    paid_note          varchar(500),
    credits_invoice_id varchar(36) references bil.invoice (id),
    reason             varchar(500)
);
create unique index uq_invoice_period on bil.invoice (df_id, period_from) where kind = 'INVOICE';
create unique index uq_invoice_credit on bil.invoice (credits_invoice_id) where credits_invoice_id is not null;
create index idx_invoice_df on bil.invoice (df_id, period_from desc);

create table bil.invoice_line (
    invoice_id   varchar(36) not null references bil.invoice (id),
    line_no      int not null,
    description  varchar(300) not null,
    plan_name    varchar(32) not null,
    from_at      timestamptz not null,
    to_at        timestamptz not null,
    qty_days     numeric(8, 2) not null,
    amount_minor bigint not null,
    primary key (invoice_id, line_no)
);

-- An issued invoice is a record: lines never change, an invoice is never deleted, and only its payment status can move.
create function bil.invoice_immutable() returns trigger language plpgsql as $$
begin
    if tg_op = 'DELETE' then
        raise exception 'invoices are never deleted';
    end if;
    if tg_table_name = 'invoice_line' then
        raise exception 'invoice lines are never changed';
    end if;
    if (old.id, old.number, old.kind, old.df_id, old.period_from, old.period_to, old.currency, old.subtotal_minor, old.tax_label,
        old.tax_bps, old.tax_minor, old.total_minor, old.issued_at, old.credits_invoice_id, old.reason)
       is distinct from
       (new.id, new.number, new.kind, new.df_id, new.period_from, new.period_to, new.currency, new.subtotal_minor, new.tax_label,
        new.tax_bps, new.tax_minor, new.total_minor, new.issued_at, new.credits_invoice_id, new.reason) then
        raise exception 'an issued invoice can only be marked paid or credited';
    end if;
    return new;
end $$;
create trigger trg_invoice_immutable before update or delete on bil.invoice
    for each row execute function bil.invoice_immutable();
create trigger trg_invoice_line_immutable before update or delete on bil.invoice_line
    for each row execute function bil.invoice_immutable();
