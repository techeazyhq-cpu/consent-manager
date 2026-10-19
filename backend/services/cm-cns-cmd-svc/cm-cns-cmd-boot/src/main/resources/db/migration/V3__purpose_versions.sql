-- Purposes with immutable versions (ADR-031). A request names a published purpose version and is made from its
-- notice in one language; the request keeps which version and notice (by hash) the principal was shown.
--
-- Requests sent before purposes were versioned named a free-text purpose code with their own notice. Each such code
-- becomes a purpose whose version 1 is published from the data those requests asked for and the latest notice sent
-- for it, so fiduciaries can keep asking for it. Every old request then points at that version 1, but keeps the
-- hash of the notice it actually carried, which may be an earlier wording.

create table cns_cmd.prps_def (
    id          varchar(36) primary key,
    df_id       varchar(36) not null,
    code        varchar(64) not null,
    created_at  timestamptz not null,
    unique (df_id, code)
);

create table cns_cmd.prps_ver (
    id            varchar(36) primary key,
    prps_def_id   varchar(36) not null references cns_cmd.prps_def (id) on delete cascade,
    ver_no        int not null,
    sts           varchar(16) not null,  -- DRAFT | PUBLISHED; a published row is never updated
    content       jsonb not null,        -- PrpsVerContent: data categories, recipients, validity, notices
    change        jsonb,                 -- PrpsChange against the version before; null while a draft
    created_at    timestamptz not null,
    published_at  timestamptz,
    unique (prps_def_id, ver_no)
);
create unique index uq_prps_ver_one_draft on cns_cmd.prps_ver (prps_def_id) where sts = 'DRAFT';

-- A published version can't be changed or removed, even by a bug in the service.
create function cns_cmd.prps_ver_immutable() returns trigger language plpgsql as $$
begin
    if old.sts = 'PUBLISHED' then
        raise exception 'purpose version % is published and can''t be changed', old.id;
    end if;
    return case when tg_op = 'DELETE' then old else new end;
end $$;
create trigger trg_prps_ver_immutable before update or delete on cns_cmd.prps_ver
    for each row execute function cns_cmd.prps_ver_immutable();

-- ---- each purpose code already used becomes a purpose, version 1 published ----

create temporary table legacy_prps on commit drop as
with uses as (
    select df_id, prps_code, d_cats, ntc_title, ntc_text, created_at from cns_cmd.cns_req
    union all
    select df_id, prps_code, d_cats, ntc_title, ntc_text, created_at from cns_cmd.cns_req_batch
),
latest as (
    select distinct on (df_id, prps_code) df_id, prps_code, ntc_title, ntc_text, created_at
    from uses
    where ntc_title <> '' and coalesce(ntc_text, '') <> ''
    order by df_id, prps_code, created_at desc
),
first_use as (
    select df_id, prps_code, min(created_at) as created_at from uses group by df_id, prps_code
),
cats as (
    select df_id, prps_code, jsonb_agg(distinct cat order by cat) as codes
    from uses, jsonb_array_elements_text(d_cats) as cat
    group by df_id, prps_code
)
select gen_random_uuid()::text as def_id,
       gen_random_uuid()::text as ver_id,
       f.df_id, f.prps_code, f.created_at,
       c.codes,
       coalesce(l.ntc_title, initcap(replace(lower(f.prps_code), '_', ' '))) as ntc_title,
       coalesce(l.ntc_text, 'This purpose was defined before notices were kept with it.') as ntc_text
from first_use f
join cats c using (df_id, prps_code)
left join latest l using (df_id, prps_code);

insert into cns_cmd.prps_def (id, df_id, code, created_at)
select def_id, df_id, prps_code, created_at from legacy_prps;

insert into cns_cmd.prps_ver (id, prps_def_id, ver_no, sts, content, change, created_at, published_at)
select ver_id, def_id, 1, 'PUBLISHED',
       jsonb_build_object(
           'name', initcap(replace(lower(prps_code), '_', ' ')),
           'description', null,
           'dCats', (select jsonb_agg(jsonb_build_object('code', cat, 'dpv', null, 'sensitivity', 'ORDINARY',
                                                         'necessary', false, 'consequence', null))
                     from jsonb_array_elements_text(codes) as cat),
           'recipients', '[]'::jsonb,
           'transfers', '[]'::jsonb,
           -- the 365 days every consent was given for until now (ADR-026)
           'retentionDays', 365,
           'requestedValidityDays', 365,
           'maxValidityDays', 365,
           'flags', '[]'::jsonb,
           'notices', jsonb_build_array(jsonb_build_object('lang', 'en', 'title', ntc_title, 'text', ntc_text))),
       jsonb_build_object('kind', 'INITIAL', 'material', '[]'::jsonb,
                          'minor', jsonb_build_array('Made from requests sent before purposes were versioned')),
       created_at, created_at
from legacy_prps;

-- ---- requests and batches name the version and notice ----

alter table cns_cmd.cns_req
    add column prps_ver_id  varchar(36) references cns_cmd.prps_ver (id),
    add column prps_ver_no  int,
    add column ntc_lang     varchar(35),
    add column ntc_hash     char(64);

alter table cns_cmd.cns_req_batch
    add column prps_ver_id  varchar(36) references cns_cmd.prps_ver (id),
    add column prps_ver_no  int,
    add column ntc_lang     varchar(35),
    add column ntc_hash     char(64);

-- the same hash as PrpsNtc.hash(): SHA-256 of the title, a blank line and the text, in UTF-8
update cns_cmd.cns_req r
set prps_ver_id = l.ver_id, prps_ver_no = 1, ntc_lang = 'en',
    ntc_hash = encode(sha256(convert_to(r.ntc_title || E'\n\n' || coalesce(r.ntc_text, ''), 'UTF8')), 'hex')
from legacy_prps l
where l.df_id = r.df_id and l.prps_code = r.prps_code;

update cns_cmd.cns_req_batch b
set prps_ver_id = l.ver_id, prps_ver_no = 1, ntc_lang = 'en',
    ntc_hash = encode(sha256(convert_to(b.ntc_title || E'\n\n' || b.ntc_text, 'UTF8')), 'hex')
from legacy_prps l
where l.df_id = b.df_id and l.prps_code = b.prps_code;

alter table cns_cmd.cns_req
    alter column prps_ver_id set not null,
    alter column prps_ver_no set not null,
    alter column ntc_lang set not null,
    alter column ntc_hash set not null;

alter table cns_cmd.cns_req_batch
    alter column prps_ver_id set not null,
    alter column prps_ver_no set not null,
    alter column ntc_lang set not null,
    alter column ntc_hash set not null;

create index idx_cns_req_prps_ver on cns_cmd.cns_req (prps_ver_id);
