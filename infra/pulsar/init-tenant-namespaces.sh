#!/usr/bin/env bash
# Pulsar standalone auto-creates topics under the built-in public/default namespace, but NOT
# a new tenant/namespace — this platform's topic naming (persistent://cm/<ctx>/...) needs the
# "cm" tenant and one namespace per bounded context created explicitly first, or every
# producer/consumer fails with PulsarClientException$TopicDoesNotExistException ("Namespace
# not found"). Run this once after `docker compose up -d pulsar` and before starting the
# backend services (discovered during Phase 1 verification, when cm-bil-svc's DfActivatedEvt
# consumer died on startup with exactly this error and never recovered until restarted).
set -euo pipefail

docker exec infra-pulsar-1 bin/pulsar-admin tenants create cm 2>&1 | grep -v "already exists" || true
for ns in dfreg cnscmd cnsqry ntf bil; do
  docker exec infra-pulsar-1 bin/pulsar-admin namespaces create "cm/$ns" 2>&1 | grep -v "already exists" || true
done

echo "cm tenant namespaces:"
docker exec infra-pulsar-1 bin/pulsar-admin namespaces list cm
