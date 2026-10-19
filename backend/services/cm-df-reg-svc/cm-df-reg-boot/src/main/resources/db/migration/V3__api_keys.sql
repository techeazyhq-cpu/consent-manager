-- API keys for fiduciaries' systems, with optional request signing, and the nonces that stop replays (ADR-041).
create table df_reg.api_key (
    id                    varchar(36) primary key,
    df_id                 varchar(36) not null references df_reg.df (id),
    label                 varchar(64) not null,
    key_prefix            varchar(16) not null,
    key_hash              char(64) not null unique,   -- SHA-256 of the key; the key itself is never kept
    signing_secret_sealed text not null,              -- AES-GCM under cm.dfreg.secrets-key
    signing_required      boolean not null default false,
    created_at            timestamptz not null,
    created_by            varchar(64),
    last_used_at          timestamptz,
    revoked_at            timestamptz
);
create index idx_api_key_df on df_reg.api_key (df_id, created_at desc);

-- A nonce is claimed for its key until its timestamp plus the window; an atomic insert across every instance.
create table df_reg.api_request_nonce (
    key_id      varchar(36) not null,
    nonce       varchar(64) not null,
    expires_at  timestamptz not null,
    primary key (key_id, nonce)
);
create index idx_api_request_nonce_expiry on df_reg.api_request_nonce (expires_at);
