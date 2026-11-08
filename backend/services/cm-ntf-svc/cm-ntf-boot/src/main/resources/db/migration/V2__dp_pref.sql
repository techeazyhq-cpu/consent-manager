-- How a principal wants to hear about new consent requests (ADR-066). One row per principal who changed anything; no row
-- means the defaults (push on, email on). The address is never kept: only a keyed hash, to recognise the address a request
-- is sent to.
create table ntf.dp_pref (
    dp_id          varchar(64) primary key,
    push_enabled   boolean not null default true,
    email_requests boolean not null default true,
    email_hash     char(64),
    updated_at     timestamptz not null
);
create index idx_dp_pref_email_hash on ntf.dp_pref (email_hash) where email_hash is not null;
