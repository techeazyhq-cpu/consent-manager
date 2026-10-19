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

# The benchmark set behind docs/security/vapt-and-performance.md, against the local stack: the expected traffic at
# 50, 75 and 100 requests a second, API key against OAuth, and a ramp to the knee. Summaries in build/reports/k6/.
#
#   KEYS_FILE=/abs/path/cm-keys.json tests/perf/run-benchmarks.sh
set -uo pipefail
cd "$(dirname "$0")/../.."
export MSYS_NO_PATHCONV=1
: "${KEYS_FILE:?Set KEYS_FILE to the file tests/security/setup-keys.mjs wrote}"
OUT=build/reports/k6
mkdir -p "$OUT"

k6() { # name, then -e settings
  local name=$1; shift
  echo "== $name"
  docker run --rm -i --add-host host.docker.internal:host-gateway -v "$KEYS_FILE:/keys.json:ro" \
    -v "$PWD/tests/perf:/perf:ro" -v "$PWD/$OUT:/out" "$@" grafana/k6:1.3.0 \
    run --quiet --summary-export "/out/$name.json" /perf/df-api.js 2>&1 | grep -E "op:|http_req_failed\.|http_reqs\.|dropped"
  sleep 20 # let queues drain between runs
}

k6 mix-50 -e SCENARIO=mix -e RATE=50 -e DURATION=2m
k6 mix-75 -e SCENARIO=mix -e RATE=75 -e DURATION=2m
k6 mix-100 -e SCENARIO=mix -e RATE=100 -e DURATION=2m
# a console token lasts minutes: take a fresh one for the OAuth side
node -e "
import('./tests/security/lib/cm1.mjs').then(async ({ dfSignIn }) => {
  const fs = await import('node:fs');
  const k = JSON.parse(fs.readFileSync(process.env.KEYS_FILE, 'utf8'));
  k.tokenB = await dfSignIn('contact@security-b.example.test');
  fs.writeFileSync(process.env.KEYS_FILE, JSON.stringify(k, null, 2), { mode: 0o600 });
})"
k6 auth-50 -e SCENARIO=auth -e RATE=50 -e DURATION=1m
k6 ramp -e SCENARIO=ramp
