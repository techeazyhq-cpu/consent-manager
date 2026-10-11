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

# Builds every backend service's container image via Spring Boot Buildpacks (plan §6 —
# "No hand-written Dockerfiles"). Run this before `docker compose up` in infra/.
set -euo pipefail
cd "$(dirname "$0")/.."

for module in cm-df-reg-boot cm-cns-cmd-boot cm-cns-qry-boot cm-ntf-boot cm-bil-boot cm-aud-boot cm-stf-boot cm-gw; do
  echo "== bootBuildImage :$module =="
  ./gradlew ":$module:bootBuildImage"
done

echo "Done. Images tagged techeazyhq/<module>:0.1.0-SNAPSHOT — see infra/docker-compose.yml."
