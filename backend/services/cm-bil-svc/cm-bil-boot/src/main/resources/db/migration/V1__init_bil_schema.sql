create schema if not exists bil;

create table bil.sub_pln (
    id                  varchar(36) primary key,
    name                varchar(32) not null unique,
    req_per_sec_qta     bigint not null,
    req_per_month_qta   bigint not null
);

create table bil.df_sub (
    df_id           varchar(36) primary key,
    sub_pln_id      varchar(36) not null references bil.sub_pln (id),
    sts             varchar(16) not null,
    cur_period_from timestamptz not null,
    cur_period_to   timestamptz not null
);

insert into bil.sub_pln (id, name, req_per_sec_qta, req_per_month_qta) values
    ('00000000-0000-0000-0000-000000000001', 'FREE', 5, 1000),
    ('00000000-0000-0000-0000-000000000002', 'STANDARD', 50, 50000),
    ('00000000-0000-0000-0000-000000000003', 'PREMIUM', 500, 1000000);
