-- A fiduciary's acceptance of the consent manager's own terms of service and data processing agreement (ADR-063).
-- Evidence: rows are only ever added.
create table df_reg.df_terms_acceptance (
    id           varchar(36) primary key,
    df_id        varchar(36) not null references df_reg.df (id),
    doc          varchar(8)  not null check (doc in ('TERMS', 'DPA')),
    version      varchar(32) not null,
    accepted_by  varchar(64) not null,
    accepted_at  timestamptz not null,
    unique (df_id, doc, version)
);
create index idx_df_terms_acceptance_df on df_reg.df_terms_acceptance (df_id, accepted_at desc);
