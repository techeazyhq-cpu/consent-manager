# ADR-070: Metrics, dashboards and alerts

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #98

## Context

Until now the platform could say whether a service was up (`/actuator/health`, the public status page: ADR-067) but not how it was
behaving: no request rates, latencies, error shares, heap or pool figures were collected anywhere, and the backup alerts that
`notifications.tf` refers to (`postgres-recovery.rules.yml`) did not exist. The performance work (ADR-052) was done by hand-timed
load runs. Two constraints shape the answer: the whole stack runs on a 15.6 GB laptop with every container capped, and the
gateway is the public edge, so anything it serves on its port is served to the internet.

## Decision

1. **Metrics first.** Every service exports Micrometer metrics in Prometheus format (`micrometer-registry-prometheus`) with
   histogram buckets for `http.server.requests`, so p95 latency can be computed. Logs (Loki) and traces (Tempo) are deliberately not
   in this change: they cost the most CPU and memory (the 8% of CPU that Spring Security's observations alone took is why they are
   switched off), and nobody has yet needed them to answer a question that metrics could not.
2. **Off unless asked for.** `/actuator/prometheus` is exposed only when `CM_METRICS_EXPOSURE` includes it (compose, Helm
   `metrics.enabled`). Every service's security permits the path on the service port, which is internal to the cluster or compose
   network and not routed by the gateway; a network policy decides who may scrape.
3. **The gateway's metrics are on another port.** With `CM_GW_MANAGEMENT_PORT` set, the gateway moves its whole actuator to that port
   (`9091`), and its security permits `/actuator/prometheus` only on a request that arrived on it. On the public port it stays 401.
   Its Helm probes follow the actuator to that port when metrics are on.
4. **Prometheus and Grafana as an opt-in compose profile** (`observability`, `scripts/observability.sh up|down`): 256 MB and 192 MB
   caps, three days or 512 MB of data, a provisioned datasource and one dashboard (traffic, 5xx share, p95, heap, CPU, OPA).
5. **Alerts as files in the repository** (`infra/observability/prometheus/rules/`): services down, a 5xx share above 5%, slow p95,
   heap above 90%; and the backup rules, fed by a textfile the backup and restore-drill scripts now write (`BACKUP_METRICS_FILE`):
   no good dump for 36 hours, the last run failed, no drill for 93 days.

## Not yet / limits

- **No Loki, no Tempo, no traces.** Log aggregation and tracing are the rest of #98.
- **Circuit-breaker state is not exported.** The Resilience4j Micrometer binder is not on the classpath, so a breaker's state cannot
  be graphed or alerted; the status page's probes remain the only signal.
- **Database pool and Pulsar figures** are absent for the same reason (no R2DBC pool metrics wired); Postgres, Redis and Pulsar have no
  exporters here.
- **Nothing routes the alerts.** The rules fire inside Prometheus; there is no Alertmanager and no pager. Where a deployment already
  has one (Prometheus Operator, a managed Prometheus), the rule files load as they are.
- **The backup alerts need the job scheduled and its textfile scraped,** which is not done by this change (the CronJob is still open
  in the backup runbook). Without the file they are silent, not failing.
- **Helm does not add a scrape allowance** to the network policy; whoever runs Prometheus adds it for ports 8081 to 8088 and 9091.

## Options considered

- **A separate management port for every service.** Cleaner, but the status page and the Helm probes read `/actuator/health` on the
  service port; moving it would break them for no gain on an internal port. The gateway, being public, is the exception.
- **Authenticating `/actuator/prometheus` with a token.** The services already treat a bearer header as a Keycloak JWT; a second
  kind of bearer would need its own filter in each. A port that is not routed is simpler and checked by the network.
- **The full stack (Grafana, Tempo, Loki, Prometheus) at once.** Rejected for the laptop's memory budget (ADR-052) and because it
  would ship unused components; metrics answer the questions the performance and recovery work actually raised.
