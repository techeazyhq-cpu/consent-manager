create schema if not exists cns_cmd;

create table cns_cmd.cns_req (
    id            varchar(36) primary key,
    df_id         varchar(36) not null,
    dp_ref_typ    varchar(16),          -- EMAIL | PHONE, null once linked
    dp_ref_val    varchar(255),         -- normalized external contact, null once linked
    dp_id         varchar(36),          -- null until linked (plan §7a)
    prps_code     varchar(64) not null,
    d_cats        jsonb not null,
    ntc_title     varchar(255) not null,
    ntc_text      text,
    sts           varchar(24) not null,
    created_at    timestamptz not null
);
create index idx_cns_req_unlinked on cns_cmd.cns_req (dp_ref_typ, dp_ref_val) where dp_id is null;
create index idx_cns_req_dp_id on cns_cmd.cns_req (dp_id);

create table cns_cmd.cns_art (
    id            varchar(36) primary key,
    cns_req_id    varchar(36) not null references cns_cmd.cns_req (id),
    df_id         varchar(36) not null,
    dp_ref_typ    varchar(16),
    dp_ref_val    varchar(255),
    dp_id         varchar(36),
    prps_code     varchar(64) not null,
    d_cats        jsonb not null,
    val_from      timestamptz not null,
    val_until     timestamptz not null,
    sts           varchar(24) not null,
    issued_at     timestamptz not null,
    cns_tkn       text
);
create index idx_cns_art_unlinked on cns_cmd.cns_art (dp_ref_typ, dp_ref_val) where dp_id is null;
create index idx_cns_art_dp_id on cns_cmd.cns_art (dp_id);
create index idx_cns_art_issued_expiry on cns_cmd.cns_art (val_until) where sts = 'ISSUED';

create table cns_cmd.outbox_evt (
    id            uuid primary key,
    evt_id        uuid not null unique,
    agg_type      varchar(64) not null,
    agg_id        varchar(36) not null,
    evt_type      varchar(128) not null,
    payload_json  text not null,
    created_at    timestamptz not null,
    published_at  timestamptz
);
create index idx_outbox_unpublished on cns_cmd.outbox_evt (created_at) where published_at is null;

create table cns_cmd.sga_state (
    saga_id       varchar(36) primary key,
    cns_id        varchar(36) not null,
    current_step  varchar(64) not null,
    status        varchar(24) not null,
    last_error    text,
    updated_at    timestamptz not null
);
