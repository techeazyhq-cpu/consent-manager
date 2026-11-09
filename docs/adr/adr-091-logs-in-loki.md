# ADR-091: Logs in Loki, next to the metrics

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #98
- **Builds on:** [ADR-070](adr-070-metrics-and-dashboards.md), [ADR-087](adr-087-datastore-metrics-and-alert-routing.md)

## Context

The metrics stack says that something is wrong; the reason is in a service's log, and on a laptop that means `docker logs` for each of
ten containers in turn. The observability profile had no logs.

## Decision

1. **Loki and Alloy join the `observability` profile.** Alloy reads the log of every container of the compose project through the
   Docker socket (read-only) and sends it to Loki, which keeps it on a volume for 7 days. Together they are capped at 256 MB and
   128 MB of memory and 0.4 CPU (ADR-052).
2. **The labels are the service and the project, and nothing that varies per line,** so the index stays small. The line is not parsed
   or changed.
3. **Grafana has Loki as a second data source** (uid `loki`), so *Explore* shows a service's log, and a panel can sit next to a
   metric. `scripts/observability.sh up` and `down` start and stop them with the rest.
4. **Nothing leaves the machine:** no authentication or multi-tenant mode, no reporting, a port that is not published.

## Not yet / limits

- **Traces (Tempo) are not here;** that is the other half of the issue.
- **The logs are as they are written:** plain lines, not JSON, so a query filters by text (`{service="cm-ntf-svc"} |= "ERROR"`), not by
  field. Structured logging would be a change in every service.
- **What a service logs is what is stored.** The services do not log addresses, numbers or codes (ADR-066, ADR-089), and the 7-day
  retention is a setting in `loki.yml`, not a legal decision about the logs of a real deployment.
- **Loki runs as root locally,** because a new named volume is owned by root; a cluster would run it from its own chart as a normal user.
- **In a cluster** the collector is the platform's (the cloud's log service, or Alloy as a DaemonSet): this is the laptop stack only.
- **No alert is made from the logs;** the metrics' alerts stand.
