-- Copyright 2026 Vasantha Kumar
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--
-- @author Vasantha Kumar <vasantha.kumar@hotmail.com>

-- The tamper-evident audit ledger (ADR-014).
create schema if not exists aud;

-- The hash chain: one row per recorded event, never changed or removed. Nothing personal: ids, types, times and
-- digests only.
create table aud.entry (
    seq             bigint primary key,
    evt_id          uuid not null unique,
    evt_type        varchar(64) not null,
    occurred_at     timestamptz not null,
    recorded_at     timestamptz not null,
    cns_req_id      varchar(64),
    cns_art_id      varchar(64),
    df_id           varchar(64),
    payload_sha256  char(64) not null,
    prev_hash       char(64) not null,
    entry_hash      char(64) not null unique
);
create index idx_entry_cns_req_id on aud.entry (cns_req_id);
create index idx_entry_cns_art_id on aud.entry (cns_art_id);
create index idx_entry_df_id on aud.entry (df_id);

create function aud.refuse_change() returns trigger language plpgsql as $$
begin
    raise exception 'aud.entry is append-only: % refused', tg_op;
end
$$;
create trigger entry_append_only before update or delete on aud.entry
    for each row execute function aud.refuse_change();
create trigger entry_no_truncate before truncate on aud.entry
    for each statement execute function aud.refuse_change();

-- What each entry recorded, possibly personal: the only data erasure may remove. The chain keeps its digest.
create table aud.entry_payload (
    seq             bigint primary key references aud.entry (seq),
    payload_json    text not null
);

-- Which principal an entry concerns, for their own view of the ledger; erasable like the payload.
create table aud.entry_subject (
    seq             bigint not null references aud.entry (seq),
    dp_id           varchar(64) not null,
    primary key (seq, dp_id)
);
create index idx_entry_subject_dp_id on aud.entry_subject (dp_id);

-- The end of the chain, locked by each append so that appends never fork it.
create table aud.chain_head (
    id              smallint primary key check (id = 1),
    seq             bigint not null,
    entry_hash      char(64) not null
);
insert into aud.chain_head (id, seq, entry_hash) values (1, 0, repeat('0', 64));
