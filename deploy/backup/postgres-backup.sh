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

# A logical backup of the platform's PostgreSQL databases (docs/runbooks/backup-and-recovery.md). It is the second line behind
# the managed service's automated backups and point-in-time recovery: a copy in another account or region, in a format that
# restores into any PostgreSQL, that a person can open and check.
#
# For each database it writes a compressed custom-format dump, proves the dump can be read back (pg_restore --list), and records
# in a manifest the dump's SHA-256 and what was in the database when it was taken: its migration versions and the row count of
# every table. The restore drill (postgres-restore-drill.sh) checks a restore against that manifest.
#
# Connection: the standard libpq variables (PGHOST, PGPORT, PGUSER, PGPASSWORD or a .pgpass / IAM token). Nothing is printed
# but names, sizes and counts: never a password, and never a row.
#
#   BACKUP_DATABASES   databases to dump, space separated (default: "consent_manager keycloak")
#   BACKUP_DIR         where the dumps and the manifest go (default: ./backups)
#   BACKUP_UPLOAD_CMD  a command run once per file with its path as the only argument, to send it off the machine,
#                      for example: aws s3 cp --sse aws:kms --no-progress   (the path is appended). Optional.
#   BACKUP_METRICS_FILE     a node_exporter textfile (*.prom) for Prometheus: last run ok, last success time (ADR-070)
#   BACKUP_KEEP_LOCAL_DAYS  delete local dumps older than this many days after a good run (default: 7; 0 keeps all)
#   PG_EXEC            a prefix that runs the PostgreSQL client tools somewhere else, e.g.
#                      "docker exec -i -e PGPASSWORD=cm -e PGUSER=cm common-postgres-1" for the local stack, whose host has
#                      no client tools (the libpq variables must be passed in with -e). Dumps stream back to this machine.
#
# Exits non-zero if any database could not be dumped, verified or uploaded; the manifest then says which.
set -euo pipefail

: "${BACKUP_DATABASES:=consent_manager keycloak}"
: "${BACKUP_DIR:=./backups}"
: "${BACKUP_KEEP_LOCAL_DAYS:=7}"
: "${PG_EXEC:=}"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$BACKUP_DIR"
manifest="$BACKUP_DIR/manifest-$stamp.json"

# PG_EXEC is split on purpose: it is a command prefix
# shellcheck disable=SC2086
pg() { if [ -n "$PG_EXEC" ]; then $PG_EXEC "$@"; else "$@"; fi; }
sha() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
size() { wc -c < "$1" | tr -d ' '; }

# the schemas that hold the platform's data: every schema but PostgreSQL's own
SCHEMA_FILTER="table_schema not in ('pg_catalog', 'information_schema')"

# row count of every user table, "schema.table=count" lines. query_to_xml counts each table exactly, with no function to install.
counts() {
  pg psql -X -At -d "$1" -c "
    select table_schema || '.' || table_name || '=' ||
           (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text
    from information_schema.tables where table_type = 'BASE TABLE' and $SCHEMA_FILTER order by 1"
}

# the migration versions each schema has applied, "schema=v1,v2,..." lines (Flyway keeps a table per schema)
migrations() {
  local db="$1" schema
  for schema in $(pg psql -X -At -d "$db" -c "select table_schema from information_schema.tables where table_name = 'flyway_schema_history' order by 1"); do
    printf '%s=%s\n' "$schema" "$(pg psql -X -At -d "$db" -c "select coalesce(string_agg(version, ',' order by installed_rank), '') from \"$schema\".flyway_schema_history where success and version is not null")"
  done
}

json_lines() { # turns "k=v" lines into a JSON object body, "k":"v",...
  awk -F= 'BEGIN{first=1} NF>=2 {k=$1; v=substr($0, length($1)+2); gsub(/\\/,"\\\\",k); gsub(/"/,"\\\"",k); gsub(/\\/,"\\\\",v); gsub(/"/,"\\\"",v);
    if (!first) printf ","; first=0; printf "\"%s\":\"%s\"", k, v}'
}

failed=0
entries=""
for db in $BACKUP_DATABASES; do
  file="$BACKUP_DIR/$db-$stamp.dump"
  status="ok"
  note=""
  echo "Dumping $db ..."
  # the counts are taken first, so a dump that is a little newer than its counts is never short of what they say
  c="$(counts "$db")" || { status="failed"; note="could not count the tables"; c=""; }
  m="$(migrations "$db")" || m=""
  if [ "$status" = "ok" ]; then
    if pg pg_dump --format=custom --compress=9 --no-owner --no-privileges "$db" > "$file"; then
      # a dump that cannot be read back is not a backup
      if ! pg pg_restore --list < "$file" > /dev/null; then status="failed"; note="the dump could not be read back"; fi
    else
      status="failed"; note="pg_dump failed"
    fi
  fi
  if [ "$status" = "ok" ] && [ -n "${BACKUP_UPLOAD_CMD:-}" ]; then
    # shellcheck disable=SC2086
    if ! $BACKUP_UPLOAD_CMD "$file"; then status="failed"; note="the upload failed"; fi
  fi
  if [ "$status" = "ok" ]; then
    echo "  $db: $(size "$file") bytes, $(printf '%s\n' "$c" | grep -c .) tables, sha256 $(sha "$file" | cut -c1-12)…"
  else
    echo "  $db: $note" >&2
    failed=1
  fi
  entries="$entries${entries:+,}{\"database\":\"$db\",\"file\":\"$(basename "$file")\",\"status\":\"$status\",\"note\":\"$note\",\"bytes\":$([ -f "$file" ] && size "$file" || echo 0),\"sha256\":\"$([ -f "$file" ] && sha "$file" || echo '')\",\"migrations\":{$(printf '%s\n' "$m" | json_lines)},\"rowCounts\":{$(printf '%s\n' "$c" | json_lines)}}"
done

printf '{"takenAt":"%s","complete":%s,"dumps":[%s]}\n' "$stamp" "$([ "$failed" = 0 ] && echo true || echo false)" "$entries" > "$manifest"
if [ -n "${BACKUP_UPLOAD_CMD:-}" ]; then
  # shellcheck disable=SC2086
  $BACKUP_UPLOAD_CMD "$manifest" || { echo "The manifest could not be uploaded" >&2; failed=1; }
fi

if [ "$failed" = 0 ] && [ "$BACKUP_KEEP_LOCAL_DAYS" -gt 0 ]; then
  find "$BACKUP_DIR" -maxdepth 1 \( -name '*.dump' -o -name 'manifest-*.json' \) -mtime "+$BACKUP_KEEP_LOCAL_DAYS" -delete
fi

# For Prometheus (ADR-070): a node_exporter textfile, rewritten each run. A failed run keeps the last success's timestamp, so
# the "no backup for 36 hours" alert counts from the last good one.
if [ -n "${BACKUP_METRICS_FILE:-}" ]; then
  last="$(sed -n 's/^cm_backup_last_success_timestamp_seconds \(.*\)$/\1/p' "$BACKUP_METRICS_FILE" 2> /dev/null || true)"
  [ "$failed" = 0 ] && last="$(date +%s)"
  {
    echo "cm_backup_last_run_success $([ "$failed" = 0 ] && echo 1 || echo 0)"
    echo "cm_backup_last_success_timestamp_seconds ${last:-0}"
  } > "$BACKUP_METRICS_FILE.tmp" && mv "$BACKUP_METRICS_FILE.tmp" "$BACKUP_METRICS_FILE"
fi

echo "Manifest: $manifest"
exit "$failed"
