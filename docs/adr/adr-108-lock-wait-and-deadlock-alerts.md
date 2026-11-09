# ADR-108: Alerts on a session stuck waiting for a lock, and on deadlocks

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #98
- **Builds on:** [ADR-092](adr-092-database-transaction-alerts.md), [ADR-087](adr-087-datastore-metrics-and-alert-routing.md)

## Context

ADR-092 alerted on a session left idle in a transaction and on a statement running for a long time, and listed lock waits and deadlocks as
not done: "the exporter's lock count does not separate waiting from granted, and the deadlock counter is not among the series this
exporter version exposes". Looked at again on the running exporter, both are there:

- **A lock wait** is a session `active` whose `wait_event_type` is `Lock`: the activity metrics (`pg_stat_activity_count` and the others) already
  carry `wait_event_type` and `wait_event` as labels. A session waiting for a row lock showed as `wait_event_type="Lock", wait_event="transactionid"`.
- **A deadlock** is counted in `pg_stat_database_deadlocks`, per database.

## Decision

1. **`DatabaseSessionWaitingForALock` (ticket):** a session waiting on a lock at every look (every scrape) for 5 minutes, per database. A
   lock wait is normal and brief on a busy table, and the exporter is looked at every 15 seconds: a wait that is still there at every
   look for 5 minutes is not contention but something holding a lock it should have let go (a transaction stuck open, ADR-092's idle
   in transaction being the usual cause), with what waits on it stuck behind it.
2. **`DatabaseDeadlock` (ticket):** the deadlock counter rose in the last 15 minutes. PostgreSQL has already broken the deadlock by aborting
   one transaction, so nothing is stuck; the alert says that two pieces of code take the same rows in opposite orders, which is a bug to
   find before it happens under load.
3. **The databases whose names start with `template` are left out.**
4. **Unit-tested with `promtool test rules`** (CI runs them, ADR-092): a wait that holds for 5 minutes fires, one that comes and goes
   does not, a template database does not, a deadlock fires when the counter rises and goes quiet 15 minutes later.

## Checked

On the local stack, with a real lock wait (one session holding a row lock, another waiting for it) and a real deadlock (two sessions taking
two rows in opposite orders): the exporter showed the waiting session with `wait_event_type="Lock"`, and `pg_stat_database_deadlocks` went from 0 to 1
(PostgreSQL reported `deadlock detected`). In a running Prometheus, both expressions returned the series for `consent_manager` (the wait, 1;
the deadlock, an increase of 1.3 over 15 minutes, as `increase` extrapolates). The probe tables were dropped afterwards.

## Not yet / limits

- **Not fired live:** that needs a lock held for 5 minutes in a running Prometheus; the timing is proved by the unit tests and the
  expressions by the check above.
- **The wait is seen by scrape, not measured:** a wait that happens to be present at each of 20 looks in a row is the alert, so a
  session that waits 4 minutes 50 seconds and one that waits 5 do not differ much; the alert is a signal to look, not a stopwatch.
- **It does not say who holds the lock:** the runbook gives the query (`pg_blocking_pids`).
- **Lock waits inside one statement of a long migration** (a Flyway change that takes an exclusive lock on a big table) will fire it once;
  that is worth a look, as it is also stopping everything else.
- **A deadlock in a database the exporter does not connect to** is not seen (it connects to `consent_manager` and, through it, the
  databases' statistics; Keycloak's own database is read the same way).
- **Alerts still have no receiver** (ADR-087).
