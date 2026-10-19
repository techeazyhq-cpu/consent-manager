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

# A Pulsar tenant and its namespaces, created if missing: production's (TENANT=cm, the pulsar-init container) and the
# sandbox's (TENANT=cm-sbx, sbx-pulsar-init; ADR-037). Run on every `docker compose up`, so a fresh or reset broker needs
# nothing by hand (ADR-004); with everything already there it changes nothing.
set -euo pipefail
ADMIN=${PULSAR_ADMIN_URL:-http://pulsar:8080}/admin/v2
TENANT=${TENANT:-cm}
NAMESPACES=${NAMESPACES:-dfreg cnscmd cnsqry ntf bil}

put() { # PUT, treating 409 (already there) as done
  code=$(curl -s -o /tmp/out -w '%{http_code}' -X PUT -H 'Content-Type: application/json' "$ADMIN/$1" -d "${2:-}")
  case "$code" in 2??|409) echo "  $1: ok ($code)" ;; *) echo "  $1: failed ($code) $(cat /tmp/out)"; exit 1 ;; esac
}

# The broker answers its admin API a while after its container starts: wait for it, up to five minutes.
for i in $(seq 1 60); do
  curl -sf -o /dev/null "$ADMIN/clusters" && break
  [ "$i" = 60 ] && { echo "  Pulsar's admin API never answered at $ADMIN"; exit 1; }
  sleep 5
done

put "tenants/$TENANT" '{"allowedClusters":["standalone"]}'
for ns in $NAMESPACES; do put "namespaces/$TENANT/$ns"; done
