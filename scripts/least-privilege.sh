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
#   up         creates the logins (deploy/database/create-runtime-roles.sh) and recreates the services with them
#   up-owners  as up, and gives each schema an owner of its own first (deploy/database/create-schema-owners.sh, ADR-110), so each service
#              migrates as that owner and not as the shared one
#   down       recreates the services as the shared owner again (the logins and owners stay; they are harmless)
set -euo pipefail
cd "$(dirname "$0")/.."

SERVICES="cm-df-reg-svc cm-cns-cmd-svc cm-cns-qry-svc cm-ntf-svc cm-bil-svc cm-aud-svc cm-dsr-svc"

case "${1:-}" in
  up|up-owners)
    export RUNTIME_PASSWORD_DF_REG="${RUNTIME_PASSWORD_DF_REG:-cm-dev-df-reg-app}" RUNTIME_PASSWORD_CNS_CMD="${RUNTIME_PASSWORD_CNS_CMD:-cm-dev-cns-cmd-app}"
    export RUNTIME_PASSWORD_CNS_QRY="${RUNTIME_PASSWORD_CNS_QRY:-cm-dev-cns-qry-app}" RUNTIME_PASSWORD_NTF="${RUNTIME_PASSWORD_NTF:-cm-dev-ntf-app}"
    export RUNTIME_PASSWORD_BIL="${RUNTIME_PASSWORD_BIL:-cm-dev-bil-app}" RUNTIME_PASSWORD_AUD="${RUNTIME_PASSWORD_AUD:-cm-dev-aud-app}"
    export RUNTIME_PASSWORD_DSR="${RUNTIME_PASSWORD_DSR:-cm-dev-dsr-app}"
    FILES="-f docker-compose.yml -f docker-compose.least-privilege.yml"
    OWNERS=0
    if [ "$1" = "up-owners" ]; then
      export OWNER_PASSWORD_DF_REG="${OWNER_PASSWORD_DF_REG:-cm-dev-df-reg-owner}" OWNER_PASSWORD_CNS_CMD="${OWNER_PASSWORD_CNS_CMD:-cm-dev-cns-cmd-owner}"
      export OWNER_PASSWORD_CNS_QRY="${OWNER_PASSWORD_CNS_QRY:-cm-dev-cns-qry-owner}" OWNER_PASSWORD_NTF="${OWNER_PASSWORD_NTF:-cm-dev-ntf-owner}"
      export OWNER_PASSWORD_BIL="${OWNER_PASSWORD_BIL:-cm-dev-bil-owner}" OWNER_PASSWORD_AUD="${OWNER_PASSWORD_AUD:-cm-dev-aud-owner}"
      export OWNER_PASSWORD_DSR="${OWNER_PASSWORD_DSR:-cm-dev-dsr-owner}"
      PG_EXEC="docker exec -i -e PGPASSWORD=cm -e PGUSER=cm common-postgres-1" PGUSER=cm bash deploy/database/create-schema-owners.sh
      FILES="$FILES -f docker-compose.schema-owners.yml"
      OWNERS=1
    fi
    PER_SCHEMA_OWNERS=$OWNERS PG_EXEC="docker exec -i -e PGPASSWORD=cm -e PGUSER=cm common-postgres-1" PGUSER=cm bash deploy/database/create-runtime-roles.sh
    cd infra
    # shellcheck disable=SC2086
    docker compose $FILES up -d --no-build $SERVICES
    ;;
  down)
    cd infra
    docker compose up -d --no-build $SERVICES
    ;;
  *)
    echo "usage: $0 up|up-owners|down" >&2
    exit 2
    ;;
esac
