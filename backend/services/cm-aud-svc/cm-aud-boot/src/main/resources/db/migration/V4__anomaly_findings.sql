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

-- Hours in which a fiduciary's activity was far above its own usual (ADR-112). Ids, a signal's name, a time and numbers: nothing about a
-- principal. Unlike aud.entry this is a working list: a finding is acknowledged by a platform administrator, and nothing here is evidence.
create table aud.anomaly (
    id               uuid primary key,
    df_id            varchar(64) not null,
    signal           varchar(48) not null,
    window_start     timestamptz not null,
    observed         integer not null,
    expected         double precision not null,
    score            double precision not null,
    detected_at      timestamptz not null,
    status           varchar(16) not null default 'OPEN',
    acknowledged_by  varchar(64),
    acknowledged_at  timestamptz,
    unique (df_id, signal, window_start)
);
create index idx_anomaly_status on aud.anomaly (status, detected_at desc);

-- The detector counts events by hour over the last weeks.
create index idx_entry_occurred_at on aud.entry (occurred_at);
