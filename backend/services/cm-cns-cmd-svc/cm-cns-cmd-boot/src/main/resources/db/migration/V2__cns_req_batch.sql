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

-- Batches of consent requests (ADR-036): one template, a list of people, sent in the background row by row.
create table cns_cmd.cns_req_batch (
    id           varchar(36) primary key,
    df_id        varchar(36) not null,
    prps_code    varchar(64) not null,
    d_cats       jsonb not null,
    ntc_title    varchar(255) not null,
    ntc_text     text not null,
    source       varchar(8) not null,    -- API | CSV | XLSX
    file_name    varchar(255),
    created_by   varchar(64),
    created_at   timestamptz not null,
    total        int not null,
    valid        int not null,
    invalid      int not null,
    duplicate    int not null,
    sent         int not null default 0,
    failed       int not null default 0,
    sts          varchar(8) not null,    -- QUEUED | SENDING | DONE
    finished_at  timestamptz
);
create index idx_cns_req_batch_df on cns_cmd.cns_req_batch (df_id, created_at desc);
create index idx_cns_req_batch_unfinished on cns_cmd.cns_req_batch (created_at) where sts <> 'DONE';

create table cns_cmd.cns_req_batch_row (
    batch_id    varchar(36) not null references cns_cmd.cns_req_batch (id) on delete cascade,
    row_no      int not null,
    contact     varchar(255) not null,   -- as written in the list
    ref         varchar(128),            -- the fiduciary's own reference, if any
    typ         varchar(16),             -- EMAIL | PHONE, null when invalid
    val         varchar(255),            -- normalised contact, null when invalid
    outcome     varchar(16) not null,    -- PENDING | SENT | INVALID | DUPLICATE | FAILED
    cns_req_id  varchar(36),
    problem     varchar(255),
    claimed_at  timestamptz,             -- taken by a worker; reclaimable when stale
    primary key (batch_id, row_no)
);
create index idx_cns_req_batch_row_pending on cns_cmd.cns_req_batch_row (batch_id, row_no) where outcome = 'PENDING';
