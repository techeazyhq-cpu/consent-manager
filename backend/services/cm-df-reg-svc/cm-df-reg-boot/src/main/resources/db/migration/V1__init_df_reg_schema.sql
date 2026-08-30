create schema if not exists df_reg;

create table df_reg.df (
    id                        varchar(36) primary key,
    name                      varchar(255) not null,
    contact_email             varchar(255) not null,
    sts                       varchar(16) not null,
    registered_at             timestamptz not null,
    cm_df_api_client_secret   text,
    activated_at              timestamptz
);

create table df_reg.outbox_evt (
    id            uuid primary key,
    evt_id        uuid not null unique,
    agg_type      varchar(64) not null,
    agg_id        varchar(36) not null,
    evt_type      varchar(128) not null,
    payload_json  text not null,
    created_at    timestamptz not null,
    published_at  timestamptz
);
create index idx_df_reg_outbox_unpublished on df_reg.outbox_evt (created_at) where published_at is null;
