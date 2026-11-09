# ADR-092: Alerts on long and idle-in-transaction database sessions

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #98
- **Builds on:** [ADR-087](adr-087-datastore-metrics-and-alert-routing.md)

## Context

The pools of nine services share one PostgreSQL (ADR-052). The failure that does not show as "down" or "connections nearly all" is a
session that holds its transaction open: it keeps its locks, so other requests wait, and a pool connection, so the pool drains. The
exporter already reports the age of the oldest transaction by state; nothing alerted on it.

## Decision

1. **`DatabaseTransactionIdleOpen`** (ticket): a session `idle in transaction` for more than 2 minutes, for 5 minutes. A well-behaved
   service never does this for more than a moment; one that does is calling something slow, or has a bug, inside a transaction.
2. **`DatabaseTransactionRunningLong`** (ticket): an `active` statement older than 10 minutes, for 5 minutes. A lock wait, a runaway
   query, or a migration or backup that is meant to take long, which is why the runbook says to ask before cancelling.
3. **The rules are unit-tested** with `promtool test rules` (synthetic series: quiet, not yet long enough, and firing), and CI now
   runs `promtool check rules` on every rule file and the tests, which it did not for any rule before.

## Not yet / limits

- **Thresholds are guesses for this system,** from what the services do (short transactions), not from measured production traffic.
  The usual long thing is a Flyway migration on a big table, which will fire the second alert once; the runbook says so.
- **Lock waits and deadlocks are not alerted on:** the exporter's lock count does not separate waiting from granted, and the deadlock
  counter is not among the series this exporter version exposes by default. A custom query would add them.
- **Not fired live:** the rules were checked and unit-tested, and the metrics were confirmed to exist on the running exporter, but a
  real held-open transaction was not run against the running Prometheus (the 5-minute `for` makes that slow).
- **Per-service attribution** (which service holds the session) is by `application_name`, which the services do not set yet.
