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

-- Which fiduciaries may draft purposes with the platform's model (ADR-114): turned on by staff at onboarding or when the fiduciary asks.
-- One row per fiduciary, holding its latest request, so it can have at most one waiting. The history is in the audit trail.
create table cns_cmd.ai_drafting_access (
    df_id          varchar(36) primary key,
    enabled        boolean not null default false,
    request_sts    varchar(8) not null default 'NONE',   -- NONE | PENDING | DECLINED
    request_note   varchar(500),
    requested_by   varchar(120),
    requested_at   timestamptz,
    decided_by     varchar(120),
    decided_at     timestamptz,
    decline_reason varchar(500),
    updated_at     timestamptz not null
);
create index ix_ai_drafting_access_pending on cns_cmd.ai_drafting_access (requested_at) where request_sts = 'PENDING';
