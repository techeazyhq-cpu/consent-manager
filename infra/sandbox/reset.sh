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

# Empties the sandbox (ADR-034: "data reset periodically"; ADR-037): its database, events, usage counters, inbox, and
# the fiduciaries' API clients and members in sbx-cm-df, whose fiduciaries are gone with the database. Accounts in
# sbx-cm-dp and sbx-cm-staff are kept. Production is never touched: every step names a sandbox-only store.
#
#   infra/sandbox/reset.sh            run from the repository root, with the stack up (docker compose --profile sandbox)
#
# Schedule it (cron, a CI job) as often as the sandbox's users are told; nightly is the default promise.
set -euo pipefail
cd "$(dirname "$0")/.."
KC=${KC:-http://localhost:8080}
SBX_SERVICES="cm-sbx-gw cm-sbx-df-reg-svc cm-sbx-cns-cmd-svc cm-sbx-cns-qry-svc cm-sbx-ntf-svc cm-sbx-bil-svc"

echo "== stopping the sandbox's services"
docker compose --profile sandbox stop $SBX_SERVICES

echo "== database cm_sandbox"
docker compose exec -T -e PGPASSWORD=cm postgres psql -U cm -d consent_manager -v ON_ERROR_STOP=1 \
  -c "drop database if exists cm_sandbox with (force)" -c "create database cm_sandbox"

echo "== Pulsar tenant cm-sbx: every topic, with its backlog and subscriptions"
# Topic by topic: brokers refuse a forced namespace delete unless configured to allow it.
docker compose exec -T pulsar bash -c '
  for ns in dfreg cnscmd cnsqry ntf bil; do
    for topic in $(curl -s "http://localhost:8080/admin/v2/persistent/cm-sbx/$ns" | tr -d "[]\"" | tr "," " "); do
      curl -s -o /dev/null -w "  ${topic#persistent://}: %{http_code}\n" -X DELETE \
        "http://localhost:8080/admin/v2/persistent/${topic#persistent://}?force=true"
    done
  done'

echo "== usage counters (Redis database 1)"
docker compose exec -T redis redis-cli -n 1 flushdb

echo "== inbox"
curl -s -o /dev/null -w "  deleted: %{http_code}\n" -X DELETE http://localhost:8125/api/v1/messages

echo "== fiduciaries' API clients and members in sbx-cm-df"
T=$(curl -s -X POST "$KC/realms/master/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=admin-cli&username=${KC_ADMIN:-admin}&password=${KC_ADMIN_PASSWORD:-admin}" \
  | python -c "import json,sys; print(json.load(sys.stdin)['access_token'])")
R="$KC/admin/realms/sbx-cm-df"
for id in $(curl -s -H "Authorization: Bearer $T" "$R/clients?max=1000" \
    | python -c "import json,sys; print(' '.join(c['id'] for c in json.load(sys.stdin) if c['clientId'].startswith('df-api-')))"); do
  curl -s -o /dev/null -X DELETE -H "Authorization: Bearer $T" "$R/clients/$id"
done
for id in $(curl -s -H "Authorization: Bearer $T" "$R/users?max=10000&briefRepresentation=false" \
    | python -c "import json,sys; print(' '.join(u['id'] for u in json.load(sys.stdin) if (u.get('attributes') or {}).get('df_id')))"); do
  curl -s -o /dev/null -X DELETE -H "Authorization: Bearer $T" "$R/users/$id"
done

echo "== starting the sandbox's services"
docker compose --profile sandbox up -d $SBX_SERVICES
echo "done"
