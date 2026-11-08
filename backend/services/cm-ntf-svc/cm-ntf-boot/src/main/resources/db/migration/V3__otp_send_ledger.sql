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

-- Every sign-in code this service has been asked to deliver, kept two days (ADR-080): a limit that holds whoever asks, because
-- the delivery of a code costs money and reaches a person who did not ask for it. The destination is a keyed hash, never the address.
create table ntf.otp_send (
    id        bigserial primary key,
    dest_hash varchar(64) not null,
    channel   varchar(16) not null,
    sent_at   timestamptz not null default now()
);
create index idx_otp_send_dest on ntf.otp_send (dest_hash, sent_at);
create index idx_otp_send_channel on ntf.otp_send (channel, sent_at);
