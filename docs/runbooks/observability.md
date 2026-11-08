# Runbook: metrics, dashboards and alerts

[ADR-070](../adr/adr-070-metrics-and-dashboards.md). Metrics are off unless asked for.

## Locally

```bash
bash scripts/observability.sh up      # recreates the services with metrics on, starts Prometheus and Grafana
bash scripts/observability.sh down    # stops them and turns the endpoint off again
```

Prometheus is at http://localhost:9090 (Status, Targets: all ten should be `up`), Grafana at http://localhost:3001 (anonymous viewer;
folder *Consent Manager*, dashboard *services*). They add about 450 MB and half a CPU, both capped. The services must have been
built (`./gradlew bootJar`, then `docker compose build`) after this change, or they have no `/actuator/prometheus`.

## In a cluster

Set `metrics.enabled: true` in the Helm values. Each pod then carries `prometheus.io/scrape`, `path` and `port` annotations
(the service's port, or 9091 for the gateway) for a Prometheus that discovers by annotation, or a PodMonitor that selects
`app.kubernetes.io/part-of: consent-manager`. Allow Prometheus to reach those ports in `networkPolicy`. Load
`infra/observability/prometheus/rules/*.yml` as rule files.

Never expose the gateway's management port (9091) through the ingress: it carries `/actuator/health` and `/actuator/gateway` as well.

## What an alert means

| Alert | Look at |
|---|---|
| `ServiceDown` | `kubectl get pods` / `docker ps`; the service's log; whether the database or Pulsar is the cause. The status page shows the same |
| `HighServerErrorRate` | the dashboard's *5xx per second* to find the service; its log for the exception; a recent deploy |
| `SlowRequests` | the service's p95 against its CPU and heap panels; `cm-cns-qry-svc` is the usual one on a laptop (vapt-and-performance.md) |
| `CircuitBreakerOpen` | a dependency is failing: the breaker's name says which (`cns-cmd`, `df-reg`, `keycloak-admin`, ...); look at that service first. The breaker closes by itself once calls succeed |
| `DatabasePoolExhausted` | every database connection of that service is in use and requests wait; look for a slow query or a stuck transaction before raising the pool size |
| `HeapNearlyFull` | the container is memory-capped (ADR-052); raise the limit or look for a leak before the OOM restart |
| `LogicalBackupMissing`, `LogicalBackupFailed` | [backup-and-recovery.md](backup-and-recovery.md): run `postgres-backup.sh` by hand and read its error |
| `RestoreDrillOverdue` | run `postgres-restore-drill.sh`; a drill that was never run is a backup that was never tried |
