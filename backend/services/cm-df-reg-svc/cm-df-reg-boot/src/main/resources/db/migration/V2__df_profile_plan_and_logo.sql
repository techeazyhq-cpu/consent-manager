-- A fiduciary's profile and the plan chosen at onboarding (DfPrfl, Df.subPlnName), and its logo (DfLogo).
alter table df_reg.df
    add column legal_name          varchar(255),
    add column contact_phone       varchar(16),
    add column website             varchar(255),
    add column registered_address  varchar(500),
    add column industry            varchar(100),
    add column cin                 varchar(21),
    add column gstin               varchar(15),
    add column dpo_name            varchar(255),
    add column dpo_email           varchar(255),
    add column sub_pln_name        varchar(64);

create table df_reg.df_logo (
    df_id         varchar(36) primary key references df_reg.df (id),
    content_type  varchar(32) not null,
    bytes         bytea not null,
    updated_at    timestamptz not null
);
