-- A fiduciary's usage reaching a share of its monthly allowance (ADR-060). The primary key is the whole rule: one alert
-- per fiduciary, month and threshold, even when several replicas sweep together.
create table bil.usg_alert (
    df_id      varchar(36) not null,
    period     char(7) not null,          -- yyyy-MM, UTC: the month usage is counted in
    threshold  int not null,              -- percent of the allowance
    used       bigint not null,           -- usage when it was raised
    quota      bigint not null,           -- the allowance then
    pln_name   varchar(32) not null,
    raised_at  timestamptz not null,
    primary key (df_id, period, threshold)
);

create index idx_usg_alert_period on bil.usg_alert (period, raised_at desc);
create index idx_usg_alert_df on bil.usg_alert (df_id, raised_at desc);
