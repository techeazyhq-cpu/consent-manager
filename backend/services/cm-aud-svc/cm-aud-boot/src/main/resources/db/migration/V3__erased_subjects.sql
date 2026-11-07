-- Principals who closed their accounts (ADR-059), by pseudonymous id, and their consent requests. An event about one
-- of them that arrives after the erasure (their own withdrawals, receipts) is chained as usual, but its payload and
-- subject link are not kept.
create table aud.erased_subject (
    kind        varchar(8) not null,   -- DP | CNS_REQ
    id          varchar(64) not null,
    erased_at   timestamptz not null,
    primary key (kind, id)
);
