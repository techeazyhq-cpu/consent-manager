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

# The sandbox's Pulsar tenant and namespaces (ADR-037), created once; run by the sbx-pulsar-init container.
set -euo pipefail
ADMIN=${PULSAR_ADMIN_URL:-http://pulsar:8080}/admin/v2

put() { # PUT, treating 409 (already there) as done
  code=$(curl -s -o /tmp/out -w '%{http_code}' -X PUT -H 'Content-Type: application/json' "$ADMIN/$1" -d "${2:-}")
  case "$code" in 2??|409) echo "  $1: ok ($code)" ;; *) echo "  $1: failed ($code) $(cat /tmp/out)"; exit 1 ;; esac
}

put tenants/cm-sbx '{"allowedClusters":["standalone"]}'
for ns in dfreg cnscmd cnsqry ntf bil; do put "namespaces/cm-sbx/$ns"; done
