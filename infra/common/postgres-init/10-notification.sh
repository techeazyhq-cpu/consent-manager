#!/bin/bash
# Copyright 2026 Vasantha Kumar
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# @author Vasantha Kumar <vasantha.kumar@hotmail.com>
#
# notification-service's database on the shared Postgres (ADR-053), as its own scripts/postgres-init sets it up when
# it runs alone: the `notification` role owns the database and runs the migrations; `notification_app`, which the
# services connect as, can only read and write rows, so a compromised service can't change the schema. Runs once, on
# the first start against an empty data directory.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    CREATE ROLE notification LOGIN PASSWORD '${NOTIFICATION_DB_OWNER_PASSWORD}';
    CREATE ROLE notification_app LOGIN PASSWORD '${NOTIFICATION_DB_APP_PASSWORD}';
    CREATE DATABASE notification OWNER notification;
    REVOKE ALL ON DATABASE notification FROM PUBLIC;
    GRANT CONNECT ON DATABASE notification TO notification, notification_app;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname notification <<-EOSQL
    ALTER SCHEMA public OWNER TO notification;
    GRANT USAGE ON SCHEMA public TO notification_app;
    -- the migrations create tables as notification, after this; every one grants the app role its rights
    ALTER DEFAULT PRIVILEGES FOR ROLE notification IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO notification_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE notification IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO notification_app;
EOSQL
