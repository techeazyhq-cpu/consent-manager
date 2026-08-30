#!/usr/bin/env bash
# Builds every backend service's container image via Spring Boot Buildpacks (plan §6 —
# "No hand-written Dockerfiles"). Run this before `docker compose up` in infra/.
set -euo pipefail
cd "$(dirname "$0")/.."

for module in cm-df-reg-boot cm-cns-cmd-boot cm-cns-qry-boot cm-ntf-boot cm-bil-boot cm-gw; do
  echo "== bootBuildImage :$module =="
  ./gradlew ":$module:bootBuildImage"
done

echo "Done. Images tagged techeazyhq/<module>:0.1.0-SNAPSHOT — see infra/docker-compose.yml."
