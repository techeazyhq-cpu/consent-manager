-- Fiduciary webhooks (ADR-047): endpoints, a delivery per message per endpoint written in the same transaction as the
-- change it reports, and a log of every attempt.

create table cns_cmd.whk_endpoint (
    id                  varchar(36) primary key,
    df_id               varchar(36) not null,
    url                 varchar(2000) not null,
    description         varchar(200),
    types               jsonb not null default '[]'::jsonb,   -- message types it wants; empty: all
    active              boolean not null,
    secret_sealed       text not null,                       -- AES-GCM sealed with cm.cnscmd.secrets-key
    prev_secret_sealed  text,                                -- the rotated-out secret, honoured until:
    prev_secret_until   timestamptz,
    created_at          timestamptz not null
);
create index idx_whk_endpoint_df on cns_cmd.whk_endpoint (df_id);

create table cns_cmd.whk_delivery (
    id               uuid primary key,
    endpoint_id      varchar(36) not null references cns_cmd.whk_endpoint (id) on delete cascade,
    df_id            varchar(36) not null,
    msg_id           varchar(64) not null,    -- webhook-id: the same on every attempt and every replay
    type             varchar(40) not null,
    payload          text not null,           -- ids and states only, never personal data
    sts              varchar(12) not null,    -- PENDING | DELIVERED | FAILED (dead-lettered)
    attempts         int not null default 0,
    next_attempt_at  timestamptz,
    last_status      int,
    last_error       varchar(300),
    delivered_at     timestamptz,
    created_at       timestamptz not null,
    unique (endpoint_id, msg_id)
);
create index idx_whk_delivery_due on cns_cmd.whk_delivery (next_attempt_at) where sts = 'PENDING';
create index idx_whk_delivery_df on cns_cmd.whk_delivery (df_id, created_at desc);

create table cns_cmd.whk_attempt (
    delivery_id  uuid not null references cns_cmd.whk_delivery (id) on delete cascade,
    n            int not null,
    at           timestamptz not null,
    status       int,
    error        varchar(300),
    millis       bigint not null,
    primary key (delivery_id, n)
);
