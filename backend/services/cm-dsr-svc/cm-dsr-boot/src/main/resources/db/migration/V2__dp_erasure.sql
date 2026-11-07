-- A principal closing their account (ADR-059): the erasure saga's state. The email is held only while it runs, for the
-- notice; once done, what remains is that this pseudonymous principal's data was erased, and when.
create table dsr.dp_erasure (
    id                  varchar(36) primary key,
    dp_id               varchar(64) not null,
    email               varchar(255),
    requested_at        timestamptz not null,
    sts                 varchar(16) not null,   -- RUNNING | DONE | STUCK
    step                varchar(32),            -- the step running or stuck; null once done
    attempts            int not null default 0,
    dispatched_at       timestamptz,
    finished_at         timestamptz,
    last_error          varchar(300),
    cns_req_ids         text not null default '[]', -- JSON array: the consent requests the consents step found
    consents_withdrawn  int not null default 0
);

create index idx_dp_erasure_dp on dsr.dp_erasure (dp_id, requested_at desc);
create index idx_dp_erasure_running on dsr.dp_erasure (dispatched_at) where sts = 'RUNNING';
-- One erasure under way per principal, even when two requests arrive together.
create unique index uq_dp_erasure_running on dsr.dp_erasure (dp_id) where sts in ('RUNNING', 'STUCK');
