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

-- Reminders for unpaid invoices (ADR-105): which step of the reminder schedule has been raised for which invoice. The key makes "already
-- raised" a no-op insert, so a sweep repeated every hour, or run by two replicas, raises each step once.

create table bil.invoice_reminder (
    invoice_id varchar(36) not null references bil.invoice (id),
    step_days  int not null check (step_days > 0),  -- days after the due date: the step of the schedule
    raised_at  timestamptz not null,
    primary key (invoice_id, step_days)
);
