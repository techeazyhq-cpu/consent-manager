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

-- Which model drafts purposes, chosen by platform staff in CM Admin (ADR-113). One row for the platform: until it exists the
-- cm.cnscmd.ai-drafting.* properties apply, as before. The key is sealed with this service's key and is never returned or audited.
create table cns_cmd.ai_drafting_config (
    id              smallint primary key check (id = 1),
    enabled         boolean not null default false,
    provider        varchar(24) not null,
    endpoint        varchar(300) not null default '',
    model           varchar(120) not null,
    api_key_sealed  text,
    timeout_seconds integer not null,
    max_tokens      integer not null,
    third_party_ack boolean not null default false,
    updated_by      varchar(120) not null default '',
    updated_at      timestamptz not null
);
