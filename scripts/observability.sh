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

# Turns the local metrics stack on or off (ADR-070).
#   up    recreates the services with /actuator/prometheus exposed (and the gateway's actuator on 9091), then starts
#         Prometheus (http://localhost:9090), Alertmanager (http://localhost:9093), Grafana (http://localhost:3001) and the PostgreSQL and Redis exporters
#   down  stops Prometheus and Grafana and recreates the services with the metrics endpoint off again
# Adds about 600 MB and 0.8 CPU to the stack, both capped (ADR-052).
set -euo pipefail
cd "$(dirname "$0")/../infra"

case "${1:-}" in
  up)
    export CM_METRICS_EXPOSURE="health,info,prometheus" CM_GW_MANAGEMENT_PORT=9091 CM_TRACING_SAMPLING="${CM_TRACING_SAMPLING:-1}"
    docker compose up -d --no-build cm-df-reg-svc cm-cns-cmd-svc cm-cns-qry-svc cm-ntf-svc cm-bil-svc cm-aud-svc cm-stf-svc cm-dsr-svc cm-gw
    docker compose --profile observability up -d prometheus grafana alertmanager postgres-exporter redis-exporter loki alloy tempo
    echo "Prometheus: http://localhost:9090   Grafana: http://localhost:3001 (Consent Manager > services)"
    ;;
  down)
    docker compose --profile observability stop prometheus grafana alertmanager postgres-exporter redis-exporter loki alloy tempo
    docker compose --profile observability rm -f prometheus grafana alertmanager postgres-exporter redis-exporter loki alloy tempo
    docker compose up -d --no-build cm-df-reg-svc cm-cns-cmd-svc cm-cns-qry-svc cm-ntf-svc cm-bil-svc cm-aud-svc cm-stf-svc cm-dsr-svc cm-gw
    ;;
  *)
    echo "usage: $0 up|down" >&2
    exit 2
    ;;
esac
