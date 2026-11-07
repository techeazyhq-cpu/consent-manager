-- A fiduciary that is leaving (ADR-064): when, who asked, where it leaves from, and whether the one cancellation is used.
alter table df_reg.df add column leaving_from varchar(16);
alter table df_reg.df add column leaving_at timestamptz;
alter table df_reg.df add column leaves_at timestamptz;
alter table df_reg.df add column leaving_by varchar(64);
alter table df_reg.df add column leave_cancellations int not null default 0;
