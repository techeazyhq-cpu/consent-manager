#!/usr/bin/env bash
#
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

# Gives each service a database login of its own that can read and write the rows of its own schema and nothing else (ADR-082).
# Until now a service ran its migrations and its queries as one login that owns every schema, so a flaw in any service could
# change or drop the tables of all the others. After this, the services connect as these logins, and only the migrations (the
# owner) can change a schema.
#
#   deploy/database/create-runtime-roles.sh [schema ...]      (default: df_reg cns_cmd cns_qry ntf bil aud dsr)
#
#   PGHOST PGPORT PGUSER PGPASSWORD   the connection of the schema owner (or an administrator): the login whose migrations created the tables
#   PG_EXEC             a prefix that runs psql somewhere else, e.g. "docker exec -i -e PGPASSWORD=cm common-postgres-1" (psql runs as the PGUSER passed in)
#   DB_NAME             the database (default: consent_manager)
#   OWNER_ROLE          the role that owns the tables and runs the migrations (default: PGUSER)
#   RUNTIME_PASSWORD_<SCHEMA>   the password for that schema's login, one variable each (RUNTIME_PASSWORD_AUD ...): required, never printed
#   WAIT_FOR_SCHEMAS_SECONDS    how long to wait for a schema that does not exist yet (the first migrations create it), polling every 5 s
#                       (default 0: a missing schema stops the script with exit code 3). The Helm hook sets it (ADR-090).
#
# Safe to run again: it creates what is missing, sets the password, and grants what the schema has now; the default privileges it sets
# cover every table a later migration creates. Nothing is revoked from anyone else and nothing is dropped.
set -euo pipefail

: "${DB_NAME:=consent_manager}"
: "${PG_EXEC:=}"
: "${WAIT_FOR_SCHEMAS_SECONDS:=0}"
OWNER_ROLE="${OWNER_ROLE:-${PGUSER:-cm}}"
schemas=("$@")
[ ${#schemas[@]} -gt 0 ] || schemas=(df_reg cns_cmd cns_qry ntf bil aud dsr)

# PG_EXEC is a command prefix, split on purpose
# shellcheck disable=SC2086
psql_run() { if [ -n "$PG_EXEC" ]; then $PG_EXEC psql "$@"; else psql "$@"; fi; }

for schema in "${schemas[@]}"; do
  case "$schema" in *[!a-z_]*|"") echo "A schema name is lowercase letters and underscores: $schema" >&2; exit 2 ;; esac
  var="RUNTIME_PASSWORD_$(printf '%s' "$schema" | tr '[:lower:]' '[:upper:]')"
  password="${!var:-}"
  [ -n "$password" ] || { echo "Set $var to the password of the $schema login" >&2; exit 2; }
  case "$password" in *\'*|*\\*) echo "$var must not contain a quote or a backslash" >&2; exit 2 ;; esac
  role="cm_${schema}_app"

  # the schema is created by the service's first migration; on a first install it may not be there yet
  waited=0
  until [ "$(psql_run -X -At -d "$DB_NAME" -c "select count(*) from information_schema.schemata where schema_name = '${schema}'")" = "1" ]; do
    if [ "$waited" -ge "$WAIT_FOR_SCHEMAS_SECONDS" ]; then
      echo "The schema ${schema} does not exist in ${DB_NAME} (waited ${waited} s): its service has not run its migrations yet" >&2
      exit 3
    fi
    sleep 5
    waited=$((waited + 5))
  done

  echo "Schema $schema: login $role ..."
  # the password is part of the statement, so it travels on standard input, never on a command line
  psql_run -X -q -v ON_ERROR_STOP=1 -d "$DB_NAME" <<SQL
do \$\$
begin
    if not exists (select 1 from pg_roles where rolname = '${role}') then
        create role ${role} login password '${password}' nosuperuser nocreatedb nocreaterole noinherit;
    else
        alter role ${role} login password '${password}' nosuperuser nocreatedb nocreaterole;
    end if;
end \$\$;
grant connect on database ${DB_NAME} to ${role};
grant usage on schema ${schema} to ${role};
grant select, insert, update, delete on all tables in schema ${schema} to ${role};
grant usage, select on all sequences in schema ${schema} to ${role};
-- tables and sequences a later migration creates
alter default privileges for role ${OWNER_ROLE} in schema ${schema} grant select, insert, update, delete on tables to ${role};
alter default privileges for role ${OWNER_ROLE} in schema ${schema} grant usage, select on sequences to ${role};
-- the migration history is the owner's business
revoke all on ${schema}.flyway_schema_history from ${role};
SQL
done
echo "Done: point each service's SPRING_R2DBC_USERNAME and PASSWORD at its login, and keep SPRING_FLYWAY_USER on the owner."
