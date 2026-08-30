create schema if not exists cns_qry;

create table cns_qry.cns_art_rd_mdl (
    cns_req_id    varchar(36) primary key,
    cns_art_id    varchar(36),
    df_id         varchar(36) not null,
    dp_id         varchar(36),
    prps_code     varchar(64) not null,
    d_cats        jsonb not null default '[]',
    lcy_sts       varchar(24) not null,
    valid_from    timestamptz,
    valid_until   timestamptz,
    created_at    timestamptz not null,
    updated_at    timestamptz not null
);
create unique index idx_cns_art_rd_mdl_art_id on cns_qry.cns_art_rd_mdl (cns_art_id) where cns_art_id is not null;
create index idx_cns_art_rd_mdl_dp_id on cns_qry.cns_art_rd_mdl (dp_id);

create table cns_qry.cns_hst_rd_mdl (
    id            uuid primary key,
    cns_req_id    varchar(36) not null,
    from_sts      varchar(24),
    to_sts        varchar(24) not null,
    occurred_at   timestamptz not null,
    evt_id        varchar(64) not null unique
);
create index idx_cns_hst_rd_mdl_cns_req_id on cns_qry.cns_hst_rd_mdl (cns_req_id, occurred_at);

create table cns_qry.prj_ckpt (
    evt_id        varchar(64) primary key,
    processed_at  timestamptz not null
);
