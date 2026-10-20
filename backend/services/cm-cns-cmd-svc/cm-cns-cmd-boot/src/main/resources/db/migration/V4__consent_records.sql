-- ISO/IEC TS 27560 consent records and receipts (ADR-025). A record per consent request, its events appended in the
-- same transaction as each change of the consent, and a signed receipt per event. Events and receipts are only ever
-- added: the triggers below refuse any change to them, even by a bug in the service.

create table cns_cmd.cns_rec (
    id           varchar(36) primary key,
    cns_req_id   varchar(36) not null unique references cns_cmd.cns_req (id),
    df_id        varchar(36) not null,
    dp_id        varchar(36),            -- the principal, pseudonymous; null until linked. Never a contact.
    prps_code    varchar(64) not null,
    prps_ver_id  varchar(36) references cns_cmd.prps_ver (id),
    prps_ver_no  int,
    ntc_lang     varchar(35),
    ntc_hash     char(64),
    created_at   timestamptz not null
);
create index idx_cns_rec_dp on cns_cmd.cns_rec (dp_id, created_at desc);

create table cns_cmd.cns_rec_evt (
    rec_id       varchar(36) not null references cns_cmd.cns_rec (id),
    seq          int not null,
    evt_id       uuid not null unique,
    occurred_at  timestamptz not null,
    type         varchar(16) not null,   -- REQUESTED | GIVEN | REFUSED | MODIFIED | RENEWED | WITHDRAWN | LAPSED
    actor        varchar(16) not null,   -- FIDUCIARY | PRINCIPAL | PLATFORM
    d_cats       jsonb,                  -- the categories in force after the event, when it sets them
    valid_from   timestamptz,
    valid_until  timestamptz,
    detail       varchar(120),
    primary key (rec_id, seq)
);

create table cns_cmd.cns_rcpt (
    id           varchar(36) primary key,
    rec_id       varchar(36) not null references cns_cmd.cns_rec (id),
    seq          int not null,
    issued_at    timestamptz not null,
    kid          varchar(64) not null,
    jws          text not null,
    sha256       char(64) not null,
    unique (rec_id, seq),
    foreign key (rec_id, seq) references cns_cmd.cns_rec_evt (rec_id, seq)
);

create function cns_cmd.append_only() returns trigger language plpgsql as $$
begin
    raise exception '% is append-only: rows are added, never changed or removed', tg_table_name;
end $$;
create trigger trg_cns_rec_evt_append_only before update or delete on cns_cmd.cns_rec_evt
    for each row execute function cns_cmd.append_only();
create trigger trg_cns_rcpt_append_only before update or delete on cns_cmd.cns_rcpt
    for each row execute function cns_cmd.append_only();

-- ---- a record for every request that already exists ----
-- Requests and grants carry their times. A refusal or a withdrawal before records began didn't keep one here: it is
-- recorded at the time records began, and says so; the audit ledger has the exact time.

insert into cns_cmd.cns_rec (id, cns_req_id, df_id, dp_id, prps_code, prps_ver_id, prps_ver_no, ntc_lang, ntc_hash,
                             created_at)
select gen_random_uuid()::text, r.id, r.df_id, r.dp_id, r.prps_code, r.prps_ver_id, r.prps_ver_no, r.ntc_lang,
       r.ntc_hash, r.created_at
from cns_cmd.cns_req r;

insert into cns_cmd.cns_rec_evt (rec_id, seq, evt_id, occurred_at, type, actor, d_cats)
select c.id, 1, gen_random_uuid(), r.created_at, 'REQUESTED', 'FIDUCIARY', r.d_cats
from cns_cmd.cns_rec c join cns_cmd.cns_req r on r.id = c.cns_req_id;

insert into cns_cmd.cns_rec_evt (rec_id, seq, evt_id, occurred_at, type, actor, detail)
select c.id, 2, gen_random_uuid(), now(), 'REFUSED', 'PRINCIPAL', 'BACKFILLED: time in the audit ledger'
from cns_cmd.cns_rec c join cns_cmd.cns_req r on r.id = c.cns_req_id
where r.sts = 'DENIED';

insert into cns_cmd.cns_rec_evt (rec_id, seq, evt_id, occurred_at, type, actor, d_cats, valid_from, valid_until)
select c.id, 2, gen_random_uuid(), a.issued_at, 'GIVEN', 'PRINCIPAL', a.d_cats, a.val_from, a.val_until
from cns_cmd.cns_rec c join cns_cmd.cns_art a on a.cns_req_id = c.cns_req_id;

insert into cns_cmd.cns_rec_evt (rec_id, seq, evt_id, occurred_at, type, actor, detail)
select c.id, 3, gen_random_uuid(), now(), 'WITHDRAWN', 'PRINCIPAL', 'BACKFILLED: time in the audit ledger'
from cns_cmd.cns_rec c join cns_cmd.cns_art a on a.cns_req_id = c.cns_req_id
where a.sts = 'REVOKED';

insert into cns_cmd.cns_rec_evt (rec_id, seq, evt_id, occurred_at, type, actor)
select c.id, 3, gen_random_uuid(), a.val_until, 'LAPSED', 'PLATFORM'
from cns_cmd.cns_rec c join cns_cmd.cns_art a on a.cns_req_id = c.cns_req_id
where a.sts = 'EXPIRED';
