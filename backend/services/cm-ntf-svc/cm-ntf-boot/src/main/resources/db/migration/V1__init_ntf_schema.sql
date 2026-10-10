create schema if not exists ntf;

-- Browsers (and later apps) a DP turned push notifications on in. A token identifies one browser profile, so it is
-- unique on its own: whoever registered it last owns it.
create table ntf.push_dvc (
    id             uuid primary key,
    dp_id          varchar(64) not null,
    token          varchar(512) not null unique,
    platform       varchar(16) not null,
    registered_at  timestamptz not null,
    last_seen_at   timestamptz not null
);
create index idx_push_dvc_dp_id on ntf.push_dvc (dp_id, last_seen_at desc);
