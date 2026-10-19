-- What billing did, for the audit ledger (ADR-040): written in the same transaction as the change, relayed to Pulsar.
create table bil.outbox_evt (
    id            uuid primary key,
    evt_id        uuid not null unique,
    agg_type      varchar(64) not null,
    agg_id        varchar(64) not null,
    evt_type      varchar(128) not null,
    payload_json  text not null,
    created_at    timestamptz not null,
    published_at  timestamptz
);
create index idx_bil_outbox_unpublished on bil.outbox_evt (created_at) where published_at is null;
