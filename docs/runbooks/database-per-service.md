# Runbook: moving a service to its own database

[ADR-074](../adr/adr-074-database-per-service.md). Example: the audit service (`cm-aud-svc`, schema `aud`).

## Before

- A new database exists, on the same server or another, with a login that owns it and can create a schema in it. On AWS, a
  server per service is Terraform: add the service to `dedicated_databases` (for example
  `{"cm-aud-svc" = {db_name = "cm_aud", username = "cm_aud"}}`), apply, and copy the `postgres_dedicated` output into the
  release's values; the owner's password is in the RDS-managed secret it names, to be copied into the application secret as
  `CM_AUD_SVC_DB_PASSWORD`. Azure and GCP: create the server by hand.
- You have a recent backup ([backup-and-recovery.md](backup-and-recovery.md)) and a quiet hour.
- The new login's password is in the release's secret under a key of its own (`AUD_DB_PASSWORD`).

## Rehearse (changes nothing)

```bash
SRC_PG_EXEC="docker exec -i -e PGPASSWORD=cm -e PGUSER=cm common-postgres-1" \
  bash deploy/backup/split-schema.sh aud cm_aud_rehearsal
```

On a cloud, run it where the client tools are, with `PGHOST` and friends for the source and `DST_PGHOST`, `DST_PGUSER`, `DST_PGPASSWORD`
for the target. It prints the tables and rows copied and exits non-zero on any difference. Drop the rehearsal database afterwards.

## Cut over

1. Scale the service to zero: `kubectl -n consent-manager scale deploy/cm-aud-svc --replicas=0`. Its writers retry or queue (the
   others call it over HTTP and Pulsar, with breakers); nothing writes to the schema now.
2. Run the script for real (the target database must not have the schema).
3. Add the override to the release's values and upgrade, with the service still at zero:

   ```yaml
   services:
     cm-aud-svc:
       database: {host: aud-db.example.com, name: cm_aud, user: cm_aud, passwordKey: AUD_DB_PASSWORD}
   ```

4. Scale it back up. In its log, Flyway must say `Schema "aud" is up to date` (it found the copied history, so it re-ran nothing).
5. Check the health endpoint, then the service's own function (for audit: the activity list, and a new consent action appearing in it).
6. Back it up: a run of `postgres-backup.sh` for the new server with its own `PGHOST`, `BACKUP_DATABASES=cm_aud` and
   `BACKUP_MANIFEST_PREFIX=aud` so its manifests stay apart (the drill takes the same prefix). The shared server's run no longer lists it.

## After

Keep the old schema for a week, read-only if you can (`revoke all on schema aud from cm`), then drop it:
`drop schema aud cascade` in the old database, by a person, once the backups include the new one.

## Roll back

Before step 6, scale to zero, remove the override, upgrade, scale up: the old schema is untouched and holds everything up to the
stop. After new writes have gone to the new database, rolling back loses them unless you copy back the same way in reverse.
