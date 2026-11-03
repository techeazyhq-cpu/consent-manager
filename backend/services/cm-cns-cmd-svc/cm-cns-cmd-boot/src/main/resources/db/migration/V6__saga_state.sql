-- Saga progress (ADR-005): sga_state was in the V1 schema but never written. Each leg now records itself, with what a
-- re-drive needs (the fiduciary and the outcome to tell it) and how many times it has been tried.

alter table cns_cmd.sga_state rename column current_step to step;
alter table cns_cmd.sga_state add column df_id varchar(36);
alter table cns_cmd.sga_state add column outcome varchar(24);
alter table cns_cmd.sga_state add column attempts integer not null default 0;

create index idx_sga_state_unfinished on cns_cmd.sga_state (updated_at) where status in ('STARTED', 'FAILED');
