-- Platform staff proposing to end a fiduciary's contract, or call it off, for a second person to approve (ADR-064 D7).
create table df_reg.leave_proposal (
    id            varchar(36) primary key,
    df_id         varchar(36) not null references df_reg.df (id),
    kind          varchar(8) not null check (kind in ('START', 'CANCEL')),
    reason        varchar(500) not null,
    proposed_by   varchar(64) not null,
    proposed_at   timestamptz not null,
    status        varchar(10) not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    decided_by    varchar(64),
    decided_at    timestamptz,
    decision_note varchar(500),
    -- the person who decides is never the one who proposed
    check (decided_by is null or decided_by <> proposed_by or status = 'WITHDRAWN')
);
-- at most one proposal waits per fiduciary
create unique index uq_leave_proposal_pending on df_reg.leave_proposal (df_id) where status = 'PENDING';
create index idx_leave_proposal_status on df_reg.leave_proposal (status, proposed_at desc);
