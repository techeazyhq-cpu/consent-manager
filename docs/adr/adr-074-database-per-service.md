# ADR-074: Moving a service to a database of its own

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #101

## Context

ADR-007 put every service in one PostgreSQL database with a schema each and said a later physical split would be mechanical.
It is: no service reads another's schema (each reaches the others over HTTP or Pulsar), every migration and query names only its
own schema, and each connects with its own URL. What was missing was the means to do it without a code change or a risky
hand-run procedure, and the reason to: the audit service writes the most and holds the most (a hundred thousand entries in a
developer's database), the consent services are the ones whose latency matters, and a recovery or a noisy neighbour should not
reach across all of them.

## Decision

1. **A service can be given its own database by values alone.** A `database:` block under a service in the Helm values overrides
   any of host, port, name, user, JDBC and R2DBC parameters, and the key of the secret holding that server's password, over
   `config.database`. What it leaves out is inherited, so the first step can be a new database on the same server and the next a
   new server. No Java changes.
2. **A script copies the schema and proves the copy** (`deploy/backup/split-schema.sh <schema> <database>`): create the database
   if missing, refuse if the schema is already there, dump only that schema, restore it, then compare every table's row count and
   the Flyway history with the source and fail on any difference. It changes nothing in the source.
3. **The cutover is a runbook, not a script,** because it needs the service stopped for the final copy: stop writes, copy, point the
   service at the new database, start it, watch, and drop the old schema only later by hand
   (`docs/runbooks/database-per-service.md`).
4. **Tested:** the script was run on the audit schema (8 tables, 118,000 entries, migrations 1 to 3) and the audit service was
   started against the copy: it validated its four migrations and reported healthy. The chart is rendered with and without an
   override, and the difference is exactly the service's URL, user and password key.

## Not yet / limits

- **The data on the old server is not removed or frozen by the script.** Writes after the copy go to the old schema; the runbook
  stops the service first. A copy under live traffic would need logical replication, which is not here.
- **Terraform creates the extra servers on AWS only.** `dedicated_databases` in the AWS datastores module (and the production
  root, which also emits the service's `database:` block in `helm_values`) builds one Multi-AZ, encrypted instance per service
  with the same backups and cross-region copy as the shared one, checked by plan tests. Azure ([ADR-095](adr-095-azure-dedicated-database-servers.md)) and GCP ([ADR-096](adr-096-gcp-dedicated-database-instances.md)) have the same.
- **Backups of a separate server** are a run of their own: `BACKUP_MANIFEST_PREFIX` keeps each server's manifests apart so the
  restore drill finds the right one. Scheduling those runs is still a job for whoever schedules the first (the CronJob is open in
  the backup runbook), and the retention job (ADR-064) still assumes one database.
- **The sandbox** and the local compose stack keep one database.
- **Per-service roles with least privilege** are done separately: [ADR-082](adr-082-least-privilege-database-logins.md), whose logins can only touch their own schema.

## Options considered

- **Moving data by Flyway or application code:** no, the dump and restore of one schema is simple, reversible and testable.
- **A script that also does the cutover:** it would hide the step that matters (stopping writes) behind a flag.
- **Logical replication for a zero-downtime move:** right for a service that cannot stop; heavier to set up, and a stop of a few
  minutes is acceptable for every service here except the consent check path, which can run on its replicas meanwhile.
