-- Principal requests (ADR-021, ADR-058): requests relayed to a fiduciary about one of the principal's consents, and
-- grievances about the platform. The principal is their subject id only: no contact details are kept here.
create table dsr.dp_req (
    id               varchar(36) primary key,
    knd              varchar(16) not null,   -- RELAY | GRIEVANCE
    dp_id            varchar(64) not null,
    df_id            varchar(36),            -- RELAY only
    cns_req_id       varchar(36),            -- RELAY only: the consent the request is about
    prps_code        varchar(64),
    rgt              varchar(16),            -- RELAY only: ACCESS | CORRECTION | ERASURE | GRIEVANCE | NOMINATION
    details          varchar(2000),
    received_at      timestamptz not null,
    due_by           timestamptz not null,   -- fixed at receipt, never moved
    sts              varchar(16) not null,   -- OPEN | ACKNOWLEDGED | RESOLVED | REFUSED | WITHDRAWN
    acknowledged_at  timestamptz,
    closed_at        timestamptz,
    closed_by        varchar(64),
    closed_by_name   varchar(255),
    response         varchar(2000),
    refusal_ground   varchar(300),
    constraint ck_dp_req_relay check (knd <> 'RELAY' or (df_id is not null and cns_req_id is not null and rgt is not null)),
    constraint ck_dp_req_refusal check (sts <> 'REFUSED' or refusal_ground is not null)
);

create index idx_dp_req_dp on dsr.dp_req (dp_id, received_at desc);
create index idx_dp_req_df on dsr.dp_req (df_id, sts, due_by) where knd = 'RELAY';
create index idx_dp_req_grievance on dsr.dp_req (sts, due_by) where knd = 'GRIEVANCE';
-- One open request per consent and right, even when two arrive together.
create unique index uq_dp_req_open on dsr.dp_req (dp_id, cns_req_id, rgt)
    where knd = 'RELAY' and sts in ('OPEN', 'ACKNOWLEDGED');

-- Actions for the audit ledger (ADR-040) and messages for fiduciaries' webhooks (ADR-047): written in the same
-- transaction as the change, relayed to Pulsar.
create table dsr.outbox_evt (
    id            uuid primary key,
    evt_id        uuid not null unique,
    agg_type      varchar(64) not null,
    agg_id        varchar(64) not null,
    evt_type      varchar(128) not null,
    payload_json  text not null,
    created_at    timestamptz not null,
    published_at  timestamptz
);
create index idx_dsr_outbox_unpublished on dsr.outbox_evt (created_at) where published_at is null;
