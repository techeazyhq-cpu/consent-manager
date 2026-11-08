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

# Copies one service's schema out of the shared database into a database of its own, possibly on another server, and proves the
# copy is complete (ADR-074). It changes nothing in the source: the service keeps running on the old schema until it is pointed
# at the new database (the runbook, docs/runbooks/database-per-service.md), and the old schema is dropped only by a person, later.
#
#   deploy/backup/split-schema.sh <schema> <target-database>
#
#   SRC_DB      the source database (default: consent_manager)
#   SRC_PG_EXEC / DST_PG_EXEC   a prefix that runs the PostgreSQL client tools against the source and the target, for example
#                               "docker exec -i -e PGPASSWORD=cm -e PGUSER=cm common-postgres-1". Empty runs them directly,
#                               with the libpq variables (PGHOST ...) for the source; the target's are DST_PGHOST, DST_PGPORT,
#                               DST_PGUSER and DST_PGPASSWORD. DST_PG_EXEC defaults to SRC_PG_EXEC.
#
# The target database is created if it is missing (the role needs CREATEDB), must not already hold the schema, and the copy
# must match the source table by table (row counts) and migration by migration (Flyway's history) or the script fails.
# Nothing is printed but names and counts: never a password, and never a row.
set -euo pipefail

schema="${1:?usage: split-schema.sh <schema> <target-database>}"
target="${2:?usage: split-schema.sh <schema> <target-database>}"
: "${SRC_DB:=consent_manager}"
: "${SRC_PG_EXEC:=}"
: "${DST_PG_EXEC=$SRC_PG_EXEC}"

case "$schema" in *[!a-z_]*|"") echo "The schema name must be lowercase letters and underscores" >&2; exit 2 ;; esac
case "$target" in *[!a-z0-9_]*|"") echo "The target database name must be lowercase letters, digits and underscores" >&2; exit 2 ;; esac

# the prefixes are command prefixes, split on purpose
# shellcheck disable=SC2086
src() { if [ -n "$SRC_PG_EXEC" ]; then $SRC_PG_EXEC "$@"; else "$@"; fi; }
# shellcheck disable=SC2086
dst() {
  if [ -n "$DST_PG_EXEC" ]; then $DST_PG_EXEC "$@"; else
    PGHOST="${DST_PGHOST:-${PGHOST:-}}" PGPORT="${DST_PGPORT:-${PGPORT:-5432}}" PGUSER="${DST_PGUSER:-${PGUSER:-}}" \
      PGPASSWORD="${DST_PGPASSWORD:-${PGPASSWORD:-}}" "$@"
  fi
}

counts() { # "table=count" lines for one schema in one database; $1 is src or dst, $2 the database
  "$1" psql -X -At -d "$2" -c "
    select table_name || '=' ||
           (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text
    from information_schema.tables where table_type = 'BASE TABLE' and table_schema = '$schema' order by 1"
}
migrations() {
  "$1" psql -X -At -d "$2" -c "select coalesce(string_agg(version, ',' order by installed_rank), '') from \"$schema\".flyway_schema_history where success and version is not null"
}

[ "$(src psql -X -At -d "$SRC_DB" -c "select count(*) from information_schema.schemata where schema_name = '$schema'")" = "1" ] \
  || { echo "The source database $SRC_DB has no schema $schema" >&2; exit 2; }

if [ "$(dst psql -X -At -d postgres -c "select count(*) from pg_database where datname = '$target'")" = "0" ]; then
  echo "Creating database $target ..."
  dst psql -X -q -d postgres -c "create database \"$target\""
fi
if [ "$(dst psql -X -At -d "$target" -c "select count(*) from information_schema.schemata where schema_name = '$schema'")" != "0" ]; then
  echo "The target database $target already has a schema $schema: refusing to overwrite it" >&2
  exit 2
fi

src_counts="$(counts src "$SRC_DB")"
src_migrations="$(migrations src "$SRC_DB")"

echo "Copying schema $schema from $SRC_DB to $target ..."
src pg_dump --format=custom --compress=9 --no-owner --no-privileges --schema="$schema" "$SRC_DB" | dst pg_restore --no-owner --no-privileges --exit-on-error --dbname "$target"

dst_counts="$(counts dst "$target")"
dst_migrations="$(migrations dst "$target")"

status=0
if [ "$src_counts" != "$dst_counts" ]; then
  echo "The row counts differ:" >&2
  diff <(printf '%s\n' "$src_counts") <(printf '%s\n' "$dst_counts") >&2 || true
  status=1
fi
if [ "$src_migrations" != "$dst_migrations" ]; then
  echo "The migration history differs: source ($src_migrations), target ($dst_migrations)" >&2
  status=1
fi
if [ "$status" = 0 ]; then
  echo "Copied and verified: $(printf '%s\n' "$src_counts" | grep -c .) tables, migrations $src_migrations."
  echo "Rows copied per table:"; printf '%s\n' "$src_counts" | sed 's/^/  /'
  echo "Next: point the service at $target (runbook), and drop schema $schema from $SRC_DB only after it has run there."
fi
exit "$status"
