-- Self-service sign-up (ADR-065): applications awaiting their contact address's confirmation, and what a fiduciary records
-- about how it came to be and whether staff declined it.
create table df_reg.df_signup (
    id            varchar(36) primary key,
    name          varchar(255) not null,
    contact_email varchar(320) not null,           -- lower case
    legal_name    varchar(255),
    industry      varchar(128),
    website       varchar(255),
    token_hash    char(64) not null,               -- SHA-256 of the mailed token; the token is never kept
    created_at    timestamptz not null,
    expires_at    timestamptz not null,
    status        varchar(16) not null check (status in ('AWAITING_EMAIL', 'VERIFIED')),
    verified_at   timestamptz,
    df_id         varchar(36)
);
create index idx_df_signup_email on df_reg.df_signup (contact_email, created_at desc);
create index idx_df_signup_created on df_reg.df_signup (created_at);

alter table df_reg.df add column signed_up_at timestamptz;
alter table df_reg.df add column rejected_at timestamptz;
alter table df_reg.df add column rejection_reason varchar(500);
