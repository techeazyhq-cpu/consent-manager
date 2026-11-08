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

# A restore drill (docs/runbooks/backup-and-recovery.md): a backup nobody has restored is a hope, not a backup. It restores
# the newest dump of each database in BACKUP_DIR into a scratch database next to the real one, checks what came back against
# the manifest the backup wrote, and drops the scratch database. The real databases are never touched.
#
# It fails when:
#   - a dump will not restore without errors, or its checksum is not the manifest's;
#   - the migration versions of a schema are not the manifest's;
#   - a table that had rows when the dump was taken is empty, or is missing;
#   - the audit ledger's chain is broken (an entry whose previous hash is not the entry before it).
# Row counts that differ from the manifest are reported, not failed: a live system keeps writing between the counts and the dump.
# On a quiet system they match, and "--exact" makes a difference fail.
#
#   deploy/backup/postgres-restore-drill.sh [--exact] [manifest.json]    (default: the newest manifest in BACKUP_DIR)
#
# BACKUP_METRICS_FILE (a *.prom path) also writes <name>.drill.prom for Prometheus. The same variables as postgres-backup.sh: PGHOST, PGPORT, PGUSER, PGPASSWORD, BACKUP_DIR, PG_EXEC. The role needs CREATEDB.
set -euo pipefail

: "${BACKUP_DIR:=./backups}"
: "${PG_EXEC:=}"
exact=0
if [ "${1:-}" = "--exact" ]; then exact=1; shift; fi
manifest="${1:-$(ls -1t "$BACKUP_DIR"/manifest-*.json 2>/dev/null | head -1)}"
[ -n "$manifest" ] && [ -f "$manifest" ] || { echo "No manifest found in $BACKUP_DIR" >&2; exit 2; }

# shellcheck disable=SC2086
pg() { if [ -n "$PG_EXEC" ]; then $PG_EXEC "$@"; else "$@"; fi; }
sha() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
SCHEMA_FILTER="table_schema not in ('pg_catalog', 'information_schema')"

# the manifest is JSON written by postgres-backup.sh; python reads it (every machine that runs the drill has python3 or python)
PY="$(command -v python3 || command -v python)"
# tr: python on Windows ends its lines with a carriage return, which would never equal a value read from psql
field() { "$PY" -c "import json,sys; d=json.load(open(sys.argv[1])); print(eval(sys.argv[2]))" "$manifest" "$1" | tr -d '\r'; }

[ "$(field "d['complete']")" = "True" ] || { echo "The manifest says its backup was not complete: restoring it would prove nothing" >&2; exit 2; }
dir="$(dirname "$manifest")"
fails=0
note() { printf '  %s\n' "$*"; }
bad() { printf '  FAIL: %s\n' "$*" >&2; fails=$((fails + 1)); }

n="$(field "len(d['dumps'])")"
for i in $(seq 0 $((n - 1))); do
  db="$(field "d['dumps'][$i]['database']")"
  file="$dir/$(field "d['dumps'][$i]['file']")"
  scratch="drill_${db}_$(date -u +%H%M%S)"
  echo "Drill: $db from $(basename "$file")"
  [ -f "$file" ] || { bad "the dump $file is missing"; continue; }
  [ "$(sha "$file")" = "$(field "d['dumps'][$i]['sha256']")" ] && note "checksum matches the manifest" || bad "the dump's checksum is not the manifest's: it changed after it was taken"

  pg psql -X -q -d postgres -c "create database \"$scratch\"" > /dev/null
  trap 'pg psql -X -q -d postgres -c "drop database if exists \"'"$scratch"'\"" >/dev/null 2>&1 || true' EXIT
  if pg pg_restore --no-owner --no-privileges --exit-on-error --dbname "$scratch" < "$file" 2> /tmp/drill-restore.err; then
    note "restored into $scratch with no errors"
  else
    bad "pg_restore failed: $(head -c 300 /tmp/drill-restore.err)"
    pg psql -X -q -d postgres -c "drop database if exists \"$scratch\"" > /dev/null || true
    continue
  fi

  # the migration versions per schema
  for schema in $(field "' '.join(d['dumps'][$i]['migrations'].keys())"); do
    want="$(field "d['dumps'][$i]['migrations']['$schema']")"
    got="$(pg psql -X -At -d "$scratch" -c "select coalesce(string_agg(version, ',' order by installed_rank), '') from \"$schema\".flyway_schema_history where success and version is not null" 2> /dev/null || echo MISSING)"
    [ "$got" = "$want" ] && note "migrations of $schema: $want" || bad "migrations of $schema are '$got', the manifest says '$want'"
  done

  # the tables: present, and not empty when they had rows
  cur="$(pg psql -X -At -d "$scratch" -c "
    select table_schema || '.' || table_name || '=' ||
           (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from %I.%I', table_schema, table_name), false, true, '')))[1]::text
    from information_schema.tables where table_type = 'BASE TABLE' and $SCHEMA_FILTER order by 1" | tr -d '\r')"
  tables=0; differ=0
  while IFS='=' read -r name want; do
    [ -n "$name" ] || continue
    tables=$((tables + 1))
    want="${want%$'\r'}"
    got="$(printf '%s\n' "$cur" | awk -F= -v n="$name" '$1 == n {print $2}')"
    got="${got%$'\r'}"
    if [ -z "$got" ]; then bad "the table $name did not come back"; continue; fi
    if [ "$want" -gt 0 ] && [ "$got" -eq 0 ]; then bad "the table $name had $want rows and came back empty"; fi
    if [ "$got" != "$want" ]; then
      differ=$((differ + 1))
      if [ "$exact" = 1 ]; then bad "$name has $got rows, the manifest says $want"; else note "$name: $got rows now, $want when counted (the system kept writing)"; fi
    fi
  done < <("$PY" -c "import json,sys; [print(k+'='+v) for k,v in json.load(open(sys.argv[1]))['dumps'][int(sys.argv[2])]['rowCounts'].items()]" "$manifest" "$i" | tr -d '\r')
  note "$tables tables checked, $differ with a different count"

  # the audit ledger's chain, where there is one
  if pg psql -X -At -d "$scratch" -c "select to_regclass('aud.entry')" | grep -q aud.entry; then
    broken="$(pg psql -X -At -d "$scratch" -c "select count(*) from aud.entry e join aud.entry p on p.seq = e.seq - 1 where e.prev_hash <> p.entry_hash")"
    entries="$(pg psql -X -At -d "$scratch" -c "select count(*) from aud.entry")"
    [ "$broken" = "0" ] && note "audit ledger: $entries entries, every link to the one before holds" || bad "audit ledger: $broken entries do not link to the one before them"
  fi

  pg psql -X -q -d postgres -c "drop database \"$scratch\"" > /dev/null
  trap - EXIT
done

if [ "$fails" -eq 0 ]; then echo "Drill passed."; else echo "Drill FAILED: $fails problem(s)." >&2; fi
if [ -n "${BACKUP_METRICS_FILE:-}" ]; then
  # the drill's own file beside the backup's, so neither overwrites the other
  drill_file="${BACKUP_METRICS_FILE%.prom}.drill.prom"
  last="$(sed -n 's/^cm_restore_drill_last_success_timestamp_seconds \(.*\)$/\1/p' "$drill_file" 2> /dev/null || true)"
  [ "$fails" -eq 0 ] && last="$(date +%s)"
  {
    echo "cm_restore_drill_last_run_success $([ "$fails" -eq 0 ] && echo 1 || echo 0)"
    echo "cm_restore_drill_last_success_timestamp_seconds ${last:-0}"
  } > "$drill_file.tmp" && mv "$drill_file.tmp" "$drill_file"
fi
exit $(( fails > 0 ? 1 : 0 ))
