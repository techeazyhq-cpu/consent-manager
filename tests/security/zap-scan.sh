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

# OWASP ZAP's API scan of the Data Fiduciary API, from its OpenAPI spec, against the local stack (localhost:9000). The
# scanner can't sign requests, so it goes through tests/security/signing-proxy.mjs, which signs each one with the key
# KEY names in KEYS_FILE. Both run on a private Docker network that only they share; nothing listens on the host.
#
#   KEYS_FILE=/abs/path/cm-keys.json tests/security/zap-scan.sh      # reports in build/reports/zap/
set -euo pipefail
cd "$(dirname "$0")/../.."
export MSYS_NO_PATHCONV=1
: "${KEYS_FILE:?Set KEYS_FILE to the file tests/security/setup-keys.mjs wrote}"
OUT=build/reports/zap
NET=cm-vapt
mkdir -p "$OUT"
chmod a+rwx "$OUT" 2>/dev/null || true

docker network create "$NET" >/dev/null 2>&1 || true
docker run -d --rm --name cm-vapt-signer --network "$NET" --add-host host.docker.internal:host-gateway \
  -e GW_BASE=http://host.docker.internal:9000 -e HOST=0.0.0.0 -e KEYS_FILE=/keys.json -e KEY="${KEY:-b}" \
  -v "$PWD/tests/security:/kit:ro" -v "$KEYS_FILE:/keys.json:ro" node:22.14.0-alpine node /kit/signing-proxy.mjs >/dev/null
trap 'docker stop cm-vapt-signer >/dev/null 2>&1; docker network rm "$NET" >/dev/null 2>&1' EXIT

# the spec, served from the signer instead of the gateway
sed 's|http://localhost:9000|http://cm-vapt-signer:18990|; /localhost:9100/,+1d' docs/api/df-api.openapi.yaml > "$OUT/spec.yaml"

# -I: report warnings without failing the run; the report is reviewed and triaged in docs/security
docker run --rm --network "$NET" --memory 1g -v "$PWD/$OUT:/zap/wrk:rw" zaproxy/zap-stable:2.16.1 \
  zap-api-scan.py -t /zap/wrk/spec.yaml -f openapi -I \
  -r zap-report.html -J zap-report.json -w zap-report.md
echo "ZAP reports in $OUT"
