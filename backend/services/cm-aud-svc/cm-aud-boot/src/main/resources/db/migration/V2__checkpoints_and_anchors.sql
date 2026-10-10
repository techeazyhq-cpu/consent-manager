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

-- Checkpoints of the audit ledger and their external timestamps (ADR-015). Both append-only.
create table aud.checkpoint (
    id                    bigint primary key,
    from_seq              bigint not null references aud.entry (seq),
    to_seq                bigint not null references aud.entry (seq),
    merkle_root           char(64) not null,
    head_hash             char(64) not null,
    prev_checkpoint_hash  char(64) not null,
    created_at            timestamptz not null,
    checkpoint_hash       char(64) not null unique,
    check (to_seq >= from_seq)
);

-- An RFC 3161 timestamp token over a checkpoint's hash, exactly as the authority returned it (DER).
create table aud.checkpoint_anchor (
    checkpoint_id   bigint primary key references aud.checkpoint (id),
    authority_url   varchar(512) not null,
    timestamped_at  timestamptz not null,
    token           bytea not null
);

create trigger checkpoint_append_only before update or delete on aud.checkpoint
    for each row execute function aud.refuse_change();
create trigger checkpoint_no_truncate before truncate on aud.checkpoint
    for each statement execute function aud.refuse_change();
create trigger checkpoint_anchor_append_only before update or delete on aud.checkpoint_anchor
    for each row execute function aud.refuse_change();
create trigger checkpoint_anchor_no_truncate before truncate on aud.checkpoint_anchor
    for each statement execute function aud.refuse_change();
