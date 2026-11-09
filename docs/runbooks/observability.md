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

## Logs

`observability.sh up` also starts Loki and a collector. In Grafana, *Explore*, pick the *Loki* data source and query by service, for
example `{service="cm-ntf-svc"} |= "ERROR"` (the services are `cm-gw`, `cm-df-reg-svc`, `keycloak`, `postgres`, ...). Seven days are kept
([ADR-091](../adr/adr-091-logs-in-loki.md)). When an alert fires, the table below says which service to open; its log is the next step.

## Traces

`observability.sh up` starts Tempo and turns sampling to 1 (every request) on every service ([ADR-093](../adr/adr-093-traces-in-tempo.md)).
In Grafana, *Explore*, the *Tempo* data source, *Search*, pick a service: one trace shows the gateway's span and the service's under
it, with the time of each. To follow a slow request, find it by duration (*Min duration*). Without the script, sampling is 0 and
nothing is recorded. In a cluster set `tracing.enabled`, `tracing.endpoint` and `tracing.sampling` in the Helm values.

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
| `DatabaseDown` | PostgreSQL is unreachable by its exporter: every service that needs it is degraded or down; check the instance, then the network |
| `DatabaseConnectionsNearlyAll` | the pools of all services add up to most of `max_connections` (ADR-052): find which database holds them (the dashboard breaks them down) before raising the maximum |
| `RedisDown`, `RedisMemoryNearlyFull` | rate limits, the status page and usage counters live in Redis; the services fail open (ADR-016), so a Redis outage is quiet and must be seen here |
| `PulsarDown` | events are not moving between services: consents recorded in one are not reaching the others; the outbox keeps them until it is back |
| `SignInCodesRefusedByTheLimit` | the notification service refused more than 20 sign-in codes in an hour (ADR-080): someone is asking for codes in bulk, or real traffic exceeds `cm.ntf.otp-limits.*`; look at the sign-in events (`cm_otp_rate_limited`) and the ledger's table for which destinations, then block the source or raise the limit |
| `SignInCodeSurge` | codes sent in the last hour are over six times the hourly average of the day before, and over 50 (ADR-112): a bot or campaign signing in in bulk, or a real event; compare with `SignInCodesRefusedByTheLimit`, then see [unusual activity](anomaly-detection.md) |
| `UnusualFiduciaryActivity` | the audit service found a fiduciary far above its own usual in some hour (ADR-112): open the Anomalies page in CM Admin; see [unusual activity](anomaly-detection.md) |
| `SignInCodeRequestsWithoutTheKey` | a request to send a code came without the service key (ADR-083): if people cannot sign in, Keycloak's `ntf-api-key` is not the service's `internal-api-key`; if sign-in works, something else is calling the endpoint: find it in the network and the service log |
| _Changing the service key_ | not an alert: the four steps of [ADR-101](../adr/adr-101-internal-key-rotation.md) (set the old value as `NTF_INTERNAL_API_KEY_PREVIOUS` and the new as `NTF_INTERNAL_API_KEY`, restart the notification service; give Keycloak the new `NTF_API_KEY`; wait for `cm_ntf_internal_key_previous_used_total` to stop rising; remove the previous). Nobody is refused at any point |
| `PulsarSubscriptionNeverCaughtUp` | a consumer's backlog has not been empty for 15 minutes: the labels say which topic and subscription (`aud-ledger-...` is the audit ledger, `dfreg-...` fiduciary emails, `cnscmd-projector-...` a read model). It is stopped, stuck on a message it keeps refusing (its log says so), or far behind. Start with that service's log and whether it is up; `pulsar-admin topics stats <topic>` shows the backlog and the oldest unacked |
| `PulsarSubscriptionBacklogLarge` | a consumer is over 1000 messages behind and alive: events arrive faster than it handles them. Check its CPU and database; the threshold is a guess (ADR-107) and may need a line per topic |
| `HeapNearlyFull` | the container is memory-capped (ADR-052); raise the limit or look for a leak before the OOM restart |
| `DatabaseTransactionIdleOpen` | a service opened a transaction and is doing something else (a call to another service, a bug): it holds locks and a pool connection. The exporter's `pg_stat_activity` says which database; `select pid, usename, application_name, state, xact_start, query from pg_stat_activity where state like 'idle in transaction%'` says which service. Terminate it only if it blocks others |
| `DatabaseSessionWaitingForALock` | a session has waited on a lock at every look for 5 minutes, so something holds a lock it should have let go. Find the holder: `select pid, pg_blocking_pids(pid) as blocked_by, usename, application_name, state, xact_start, left(query, 80) from pg_stat_activity where wait_event_type = 'Lock'`, then look at the blocker with the same view. Usually it is a transaction idle in a transaction (the alert above). Terminate the holder (`pg_terminate_backend`) only if you know what it was doing |
| `DatabaseDeadlock` | PostgreSQL aborted a transaction to break a deadlock: nothing is stuck now. The database log has `deadlock detected` with the two statements and the rows; two code paths take the same rows in opposite orders, and one must change (take them in one order) before it happens under load. Ask whether the aborted request was retried |
| `DatabaseTransactionRunningLong` | a statement has run over 10 minutes: a slow query, a lock wait (`pg_stat_activity.wait_event_type = 'Lock'`), or a migration or backup that is meant to; ask which before cancelling |
| `LogicalBackupMissing`, `LogicalBackupFailed` | [backup-and-recovery.md](backup-and-recovery.md): run `postgres-backup.sh` by hand and read its error |
| `RestoreDrillOverdue` | run `postgres-restore-drill.sh`; a drill that was never run is a backup that was never tried |
