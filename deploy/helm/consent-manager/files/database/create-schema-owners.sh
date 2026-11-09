#!/usr/bin/env bash
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

# Gives each schema an owner of its own (ADR-110). ADR-082 gave each service a login that can only read and write its own schema, but the
# migrations, which can change or drop anything, still ran as one login that owns every schema. After this, each service migrates as
# cm_<schema>_owner, which owns its schema and can touch nothing in another: a flaw in one service's migration can no longer reach the others.
#
#   deploy/database/create-schema-owners.sh [schema ...]      (default: df_reg cns_cmd cns_qry ntf bil aud dsr)
#
#   PGHOST PGPORT PGUSER PGPASSWORD   the connection of an administrator: the login that owns the schemas today, or the cloud's master user
#   PG_EXEC             a prefix that runs psql somewhere else, e.g. "docker exec -i -e PGPASSWORD=cm common-postgres-1" (psql runs as the PGUSER passed in)
#   DB_NAME             the database (default: consent_manager)
#   OWNER_PASSWORD_<SCHEMA>   the password of that schema's owner, one variable each (OWNER_PASSWORD_AUD ...): required, never printed
#
# It runs BEFORE the services' first migration (it creates each schema, owned by its owner) or later (it hands an existing schema, and
# every table, sequence, view, function and type in it, to its owner). Safe to run again: it creates what is missing, sets the passwords
# and hands over what is not yet handed over. Nothing is dropped and no data is touched. Then run create-runtime-roles.sh with
# PER_SCHEMA_OWNERS=1 so the logins' default privileges follow the new owners.
set -euo pipefail

: "${DB_NAME:=consent_manager}"
: "${PG_EXEC:=}"
schemas=("$@")
[ ${#schemas[@]} -gt 0 ] || schemas=(df_reg cns_cmd cns_qry ntf bil aud dsr)

# PG_EXEC is a command prefix, split on purpose
# shellcheck disable=SC2086
psql_run() { if [ -n "$PG_EXEC" ]; then $PG_EXEC psql "$@"; else psql "$@"; fi; }

for schema in "${schemas[@]}"; do
  case "$schema" in *[!a-z_]*|"") echo "A schema name is lowercase letters and underscores: $schema" >&2; exit 2 ;; esac
  var="OWNER_PASSWORD_$(printf '%s' "$schema" | tr '[:lower:]' '[:upper:]')"
  password="${!var:-}"
  [ -n "$password" ] || { echo "Set $var to the password of the $schema owner" >&2; exit 2; }
  case "$password" in *\'*|*\\*) echo "$var must not contain a quote or a backslash" >&2; exit 2 ;; esac
  role="cm_${schema}_owner"

  echo "Schema $schema: owner $role ..."
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
-- create: each service's first migration begins "create schema if not exists", and PostgreSQL checks the right to create a schema before it
-- checks whether it exists, so the owner needs it (ADR-110). It lets the owner create schemas of its own, and nothing in anyone else's.
grant connect, create on database ${DB_NAME} to ${role};
-- the one running this must be able to act as the new owner to hand things to it (the cloud's master user is not a superuser)
grant ${role} to current_user;
do \$\$
declare
    r record;
begin
    if not exists (select 1 from pg_namespace where nspname = '${schema}') then
        create schema ${schema} authorization ${role};
        return;
    end if;
    execute 'alter schema ${schema} owner to ${role}';
    -- tables, views, materialized views, foreign tables and sequences (an identity or serial column's sequence follows its table)
    for r in
        select c.oid::regclass::text as name
        from pg_class c
        where c.relnamespace = '${schema}'::regnamespace and c.relkind in ('r', 'p', 'v', 'm', 'f', 'S')
          and pg_get_userbyid(c.relowner) <> '${role}'
          and not exists (select 1 from pg_depend d where d.classid = 'pg_class'::regclass and d.objid = c.oid and d.deptype in ('a', 'i'))
    loop
        execute format('alter table %s owner to ${role}', r.name);
    end loop;
    for r in
        select p.oid::regprocedure::text as name, p.prokind
        from pg_proc p
        where p.pronamespace = '${schema}'::regnamespace and pg_get_userbyid(p.proowner) <> '${role}'
    loop
        execute format('alter %s %s owner to ${role}', case r.prokind when 'p' then 'procedure' else 'function' end, r.name);
    end loop;
    for r in
        select t.oid::regtype::text as name, t.typtype
        from pg_type t
        where t.typnamespace = '${schema}'::regnamespace and t.typtype in ('e', 'd') and pg_get_userbyid(t.typowner) <> '${role}'
    loop
        execute format('alter %s %s owner to ${role}', case r.typtype when 'd' then 'domain' else 'type' end, r.name);
    end loop;
end \$\$;
SQL
done
echo "Done: run create-runtime-roles.sh with PER_SCHEMA_OWNERS=1, then point each service's SPRING_FLYWAY_USER and PASSWORD at its owner."
