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

# Pulsar standalone auto-creates topics under the built-in public/default namespace, but NOT
# a new tenant/namespace — this platform's topic naming (persistent://cm/<ctx>/...) needs the
# "cm" tenant and one namespace per bounded context created explicitly first, or every
# producer/consumer fails with PulsarClientException$TopicDoesNotExistException ("Namespace
# not found"). Run this once after `docker compose up -d pulsar` and before starting the
# backend services (discovered during Phase 1 verification, when cm-bil-svc's DfActivatedEvt
# consumer died on startup with exactly this error and never recovered until restarted).
set -euo pipefail

docker exec infra-pulsar-1 bin/pulsar-admin tenants create cm 2>&1 | grep -v "already exists" || true
for ns in dfreg cnscmd cnsqry ntf bil stf; do
  docker exec infra-pulsar-1 bin/pulsar-admin namespaces create "cm/$ns" 2>&1 | grep -v "already exists" || true
done

echo "cm tenant namespaces:"
docker exec infra-pulsar-1 bin/pulsar-admin namespaces list cm
