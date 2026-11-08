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

# Runs the local stack with each service connecting as a login that can only touch its own schema (ADR-082).
#   up    creates the logins (deploy/database/create-runtime-roles.sh) and recreates the services with them
#   down  recreates the services as the owner again (the logins stay; they are harmless)
set -euo pipefail
cd "$(dirname "$0")/.."

SERVICES="cm-df-reg-svc cm-cns-cmd-svc cm-cns-qry-svc cm-ntf-svc cm-bil-svc cm-aud-svc cm-dsr-svc"

case "${1:-}" in
  up)
    export RUNTIME_PASSWORD_DF_REG="${RUNTIME_PASSWORD_DF_REG:-cm-dev-df-reg-app}" RUNTIME_PASSWORD_CNS_CMD="${RUNTIME_PASSWORD_CNS_CMD:-cm-dev-cns-cmd-app}"
    export RUNTIME_PASSWORD_CNS_QRY="${RUNTIME_PASSWORD_CNS_QRY:-cm-dev-cns-qry-app}" RUNTIME_PASSWORD_NTF="${RUNTIME_PASSWORD_NTF:-cm-dev-ntf-app}"
    export RUNTIME_PASSWORD_BIL="${RUNTIME_PASSWORD_BIL:-cm-dev-bil-app}" RUNTIME_PASSWORD_AUD="${RUNTIME_PASSWORD_AUD:-cm-dev-aud-app}"
    export RUNTIME_PASSWORD_DSR="${RUNTIME_PASSWORD_DSR:-cm-dev-dsr-app}"
    PG_EXEC="docker exec -i -e PGPASSWORD=cm -e PGUSER=cm common-postgres-1" PGUSER=cm bash deploy/database/create-runtime-roles.sh
    cd infra
    docker compose -f docker-compose.yml -f docker-compose.least-privilege.yml up -d --no-build $SERVICES
    ;;
  down)
    cd infra
    docker compose up -d --no-build $SERVICES
    ;;
  *)
    echo "usage: $0 up|down" >&2
    exit 2
    ;;
esac
