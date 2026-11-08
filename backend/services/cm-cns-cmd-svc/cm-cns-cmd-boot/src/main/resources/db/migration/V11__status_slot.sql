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

-- Each consent token a fiduciary is issued has a place in that fiduciary's status list (ADR-077). The index is chosen at
-- random, once per consent, and is unique within the fiduciary; it says nothing about whose consent it is.
create table cns_cmd.status_slot (
    df_id      varchar(36) not null,
    idx        int not null,
    cns_art_id varchar(36) not null unique,
    primary key (df_id, idx)
);
