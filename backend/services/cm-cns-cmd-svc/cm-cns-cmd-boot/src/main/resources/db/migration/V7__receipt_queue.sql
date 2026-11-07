-- Record events still waiting for their receipt (ADR-025). The receipt job found them by comparing every event with
-- every receipt: a pass every 2 s that read both tables in full, so its cost grew with each consent ever recorded
-- (158 ms a pass at 43,000 events under load, docs/security/vapt-and-performance.md). An event now joins this queue
-- in the statement that appends it and leaves it in the statement that saves its receipt, so a pass reads only what
-- is waiting. Unlike the events and receipts themselves, rows here are deleted: it is a work list, not a record.

create table cns_cmd.cns_rcpt_pending (
    rec_id      varchar(36) not null,
    seq         integer     not null,
    occurred_at timestamptz not null,
    primary key (rec_id, seq),
    foreign key (rec_id, seq) references cns_cmd.cns_rec_evt (rec_id, seq)
);

create index idx_cns_rcpt_pending_occurred on cns_cmd.cns_rcpt_pending (occurred_at);

-- What the old pass would have found: events recorded before this migration without a receipt yet.
insert into cns_cmd.cns_rcpt_pending (rec_id, seq, occurred_at)
select e.rec_id, e.seq, e.occurred_at
from cns_cmd.cns_rec_evt e
where not exists (select 1 from cns_cmd.cns_rcpt r where r.rec_id = e.rec_id and r.seq = e.seq);
