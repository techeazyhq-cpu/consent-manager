-- A fiduciary asking for another plan, and the platform's decision (ADR-039).
create table bil.pln_chg_req (
    id                varchar(36) primary key,
    df_id             varchar(36) not null,
    from_pln_name     varchar(32) not null,
    to_pln_name       varchar(32) not null,
    note              varchar(500),
    requested_by      varchar(64) not null,
    requested_by_name varchar(255),
    requested_at      timestamptz not null,
    sts               varchar(16) not null,
    decided_by        varchar(64),
    decided_by_name   varchar(255),
    decided_at        timestamptz,
    decision_note     varchar(500)
);

create index idx_pln_chg_req_df_id on bil.pln_chg_req (df_id, requested_at desc);
create index idx_pln_chg_req_sts on bil.pln_chg_req (sts, requested_at);
-- At most one request waiting per fiduciary, even when two arrive together.
create unique index uq_pln_chg_req_pending on bil.pln_chg_req (df_id) where sts = 'PENDING';
