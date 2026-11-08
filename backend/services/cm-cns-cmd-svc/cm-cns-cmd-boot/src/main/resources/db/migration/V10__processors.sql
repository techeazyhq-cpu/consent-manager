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

-- A fiduciary's register of the processors that handle personal data for it (ADR-075). Purposes name them in their recipients
-- and transfers; a retired processor can no longer be named by a new draft or publication.
create table cns_cmd.processor (
    id            varchar(36) primary key,
    df_id         varchar(36) not null,
    name          varchar(200) not null,
    role          varchar(500) not null default '',
    country       varchar(2) not null default '',
    contact_email varchar(254) not null default '',
    sts           varchar(8) not null default 'ACTIVE',  -- ACTIVE | RETIRED
    created_at    timestamptz not null,
    retired_at    timestamptz
);
create unique index uq_processor_name on cns_cmd.processor (df_id, lower(name));
