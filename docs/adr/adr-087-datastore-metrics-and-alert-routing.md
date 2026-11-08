# ADR-087: Metrics from the datastores, and somewhere for alerts to go

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #98

## Context

ADR-070 gave every service metrics and alerts, and ADR-070's own list of what was missing still held two things that mattered. The
servers the services depend on (PostgreSQL, Redis, Pulsar) were seen only from their clients: a service could say its pool was
exhausted, not that the database was at 80% of its connections or that Redis had stopped. And the rules fired inside Prometheus with
nowhere to go: nobody is told by an alert that only a person looking at the Prometheus page can see.

## Decision

1. **Exporters in the observability profile:** `postgres_exporter` and `redis_exporter` as small containers (48 MB and 32 MB), and
   Pulsar's own `/metrics` endpoint scraped directly. All three are scraped by Prometheus with a `service` label like everything else.
2. **Five alerts on them** (`datastores.rules.yml`): the database unreachable (page), more than 80% of its connections open (ticket), Redis
   unreachable (page: the services fail open when it is down, ADR-016, so nothing else would show it), Redis above 90% of the memory it
   is allowed (only if it has a limit), Pulsar unreachable (page). The earlier backup rules and the service rules are unchanged.
3. **Alertmanager** in the profile, with one route by severity: `page` and everything else, grouped by alert and service, a short
   wait so one cause is one message, repeated hourly for a page and every four hours otherwise, and an inhibition so a service that is
   down says that one thing and not its other symptoms. Both receivers are empty locally, so nothing leaves the machine; the file
   shows where a webhook, PagerDuty or email configuration goes.
4. **Dashboard panels:** PostgreSQL connections (all and per database), Redis memory and commands per second, and a one-number
   check that all three servers are up.
5. **Checked live:** all three are scraped; every metric the five rules and four panels use returns data; both rule files and the
   Prometheus and Alertmanager configurations pass `promtool` and `amtool`; and a temporary rule that always fires reached
   Alertmanager with its severity, then was removed.

## Not yet / limits

- **No receiver is configured,** because that needs an address or a key that belongs to whoever runs the platform. Until one is set,
  alerts are seen in Alertmanager's page and Prometheus's, not delivered.
- **Cloud-managed servers** (RDS, ElastiCache, a managed Pulsar) report through the provider's own metrics, not these exporters; the
  rule files load unchanged where the same metric names are available, and the provider's alarms in Terraform (ADR-042) cover the rest.
- **Pulsar backlog and consumer lag are not alerted on yet,** because what a normal backlog is depends on the topic; the metrics are
  scraped, so a panel or rule can be added once a normal level is known.
- **Postgres lock waits, long transactions and replication** are not covered; the exporter has them and they are not wired.
- **Logs and traces (Loki, Tempo)** are still not here.

## Options considered

- **Reading the same figures from the services' pools:** shows a client's view, which is blind to the server being down.
- **A hosted alerting service:** needs an account and sends the platform's alert text outside; the file works with whatever the
  operator already runs.
