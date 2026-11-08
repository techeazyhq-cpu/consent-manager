# Runbook: backup and recovery

For on-call engineers and whoever owns the platform's data. It says what is backed up, how to get it back, and how to know the
restore is good. The scripts are in [`deploy/backup/`](../../deploy/backup/); the infrastructure that holds the backups is in
[`deploy/terraform/`](../../deploy/terraform/) (RDS automated backups with point-in-time recovery and a replica in the recovery
region, and a logical-dump bucket there, on each cloud).

> **The recovery targets below are proposals.** Nobody has accepted them, and they have to be confirmed with the owner of the
> platform and checked against what a regulator or a customer contract may require.

## What is where

| Store | What it holds | Authoritative? | If it is lost |
|---|---|---|---|
| **PostgreSQL `consent_manager`** | Every service's schema: fiduciaries and their keys, consent requests, consents, records, receipts, purposes, the audit ledger, billing, requests and grievances, notification preferences | **Yes** | Restore it (below). This is the whole platform |
| **PostgreSQL `keycloak`** | Every principal's, member's and staff member's account, in the realms | **Yes** | Restore it. Without it nobody can sign in, and a principal's account (and so their pseudonymous id) is gone |
| **PostgreSQL `notification`** | notification-service's own data (shared server, ADR-053) | Not ours | That service's own runbook. Back it up with the same job if you run the same server |
| **Pulsar** | The events between services | **No**: transport | Restart it; replay from the outboxes (below). Nothing is lost that a service had not yet published, because each service writes its events to its own database first (transactional outbox) |
| **Redis** | Rate-limit counters, usage counters for the current month, caches | **No** | Restart it. Counters start from zero: this month's usage count (billing) is wrong, and there is no tool yet to correct it from the ledger; rate limits reset |
| **Secrets** (`DFREG_SECRETS_KEY`, `CNSCMD_SECRETS_KEY`, `NTF_EMAIL_FINGERPRINT_KEY`, `CNS_TKN_JWKS`, `CNS_RCPT_JWKS`) | The keys the platform seals and signs with | **Yes, and not in the database** | See "Keys" below: this is the part a database restore does not give back |

Everything in the read model (`cns_qry`) can be rebuilt from the events; it is not a separate thing to protect, but restoring it is
faster than rebuilding it.

## Targets (proposed)

| | Target | How it is met |
|---|---|---|
| **RPO**, losing the database instance or the zone | at most **5 minutes** | RDS / Flexible Server / Cloud SQL point-in-time recovery |
| **RPO**, losing the region | at most **the replicated backup's age**, minutes to a day | the automated backups copied to the recovery region; the logical dumps, daily, as the floor |
| **RPO**, someone deletes or corrupts data | back to just before it | point-in-time recovery into a *new* instance, then copy back what is needed |
| **RTO**, the instance | **2 hours** | restore + point services at it + the checks below |
| **RTO**, the region | **8 hours** | the same, in the recovery region, with Terraform and the Helm charts ready |

None of these has been rehearsed end to end on a cloud. The restore drill below has, on the local stack.

## What runs, routinely

1. **The cloud's automated backups and point-in-time recovery**, kept `backup_retention_days` (default 30, 7 to 35), copied to the
   recovery region (`aws_db_instance_automated_backups_replication` and its Azure and GCP equivalents). Failed backups, failovers
   and low storage are reported to the operations topic (`notifications.tf`).
2. **A daily logical dump** with [`deploy/backup/postgres-backup.sh`](../../deploy/backup/postgres-backup.sh), of `consent_manager` and
   `keycloak`, uploaded to the backup bucket in the recovery region (`BACKUP_UPLOAD_CMD`), kept `logical_backup_retention_days`
   (30). It proves each dump can be read back, and writes a manifest with each dump's checksum, the migration versions and the row
   count of every table. A dump is a second opinion in another account, in a format any PostgreSQL restores: it is the copy that
   survives the loss of the cloud account's backups.
3. **A restore drill, monthly,** with [`deploy/backup/postgres-restore-drill.sh`](../../deploy/backup/postgres-restore-drill.sh): it
   restores the newest dump into a scratch database next to the live one, checks it against the manifest, and drops it. **Treat a
   failing drill as an incident**, not a nuisance: the backup you would have used does not work.

**Not scheduled yet:** the chart does not run the dump as a CronJob, and nothing alerts when the daily dump or the drill did not
run. Until that exists, whoever owns operations has to schedule `postgres-backup.sh` (a Kubernetes CronJob with an image that has
the PostgreSQL client and the cloud's CLI, using the same identity as the backup bucket's policy) and watch its exit status. The
alert rules `notifications.tf` mentions now exist ([`postgres-recovery.rules.yml`](../../infra/observability/prometheus/rules/postgres-recovery.rules.yml), ADR-070):
set `BACKUP_METRICS_FILE` to a node_exporter textfile and they fire when no dump has succeeded for 36 hours, the last run failed,
or no restore drill has passed for 93 days. They need that file scraped, which is still part of scheduling the job.

### Running them

```bash
# against a database reachable from here (libpq variables; an IAM token works as the password)
PGHOST=... PGUSER=... PGPASSWORD=... BACKUP_DIR=/var/backups/cm \
  BACKUP_UPLOAD_CMD="aws s3 cp --sse aws:kms --no-progress" deploy/backup/postgres-backup.sh
PGHOST=... PGUSER=... PGPASSWORD=... BACKUP_DIR=/var/backups/cm deploy/backup/postgres-restore-drill.sh

# the local stack, whose host has no PostgreSQL tools: run them inside the container
export PG_EXEC="docker exec -i -e PGPASSWORD=cm -e PGUSER=cm common-postgres-1" BACKUP_DIR=/tmp/cm-backups
deploy/backup/postgres-backup.sh && deploy/backup/postgres-restore-drill.sh
```

On the local stack, with about 120 MB of test data, the dump takes about two minutes and the drill about three. The drill passes
when the dump restores with no errors, its checksum is the manifest's, every schema has the migrations the manifest lists, no table
that had rows came back empty or missing, and the audit ledger's chain links hold. Row counts that differ are reported, not failed,
since a live system keeps writing; `--exact` makes them fail, for a quiet system.

## Recovering

**First, stop the writes.** Scale the services to zero (or put the gateway in maintenance) before restoring over or beside live data,
so nothing writes to a database that is about to change. Keep Pulsar and Keycloak up unless they are what failed.

### A. Someone deleted or corrupted data (the instance is fine)

1. Find the time just before it happened (the audit ledger and the services' logs have it).
2. **Restore to a new instance** at that time (RDS: *Restore to point in time*; Flexible Server: *Restore*; Cloud SQL: *Clone* to a
   point in time). Never restore over the live instance.
3. Copy back only what was lost (`pg_dump -t` from the new instance, or `INSERT ... SELECT` through a foreign table), for the rows
   concerned. The consent events, receipts and published purposes are append-only: an insert is allowed, an update is not.
4. Check (below), then delete the new instance.

### B. The database instance is lost

1. Restore to the **latest restorable time** into a new instance in the same region (point-in-time), with the same parameter
   group, security group and KMS key (`terraform apply` makes them; do not click them together by hand).
2. Point the services at it: the database endpoint is in the chart's values for the cloud (`config.database.host` in `values.yaml`,
   overridden in `values-<cloud>.yaml`), and the passwords are in the `cm-secrets` Secret. Change them, then `helm upgrade`. The migration user and the
   service user are the same roles as before.
3. Run **After any restore** below.

### C. The region is lost

1. In the recovery region, restore from the **replicated automated backup** (RDS: *Restore to point in time* from the replicated
   backup) and bring the infrastructure up with the Terraform in `deploy/terraform/<cloud>/production` against the recovery region.
2. If the replicated backup is missing or too old, restore the newest **logical dump** from the backup bucket there:
   ```bash
   createdb consent_manager && pg_restore --no-owner --no-privileges --exit-on-error -d consent_manager consent_manager-<stamp>.dump
   createdb keycloak && pg_restore --no-owner --no-privileges --exit-on-error -d keycloak keycloak-<stamp>.dump
   ```
   then create the roles the services use and grant them the schemas (`deploy/helm/consent-manager` `databaseInit`).
   A logical dump is as old as its stamp: that is the RPO of this path.
3. Restore the **secrets** (below), point DNS and the gateway at the new region, run **After any restore**.

### D. Keycloak alone

Its realms are imported from `infra/keycloak/realm-export` on first start only. A fresh Keycloak has the realms and the seeded local
staff, **not the people**. Restore the `keycloak` database (A or B above). Never recreate Keycloak empty in production: every
principal's account and id would be new, and their consents would belong to nobody.

### E. Pulsar

Start it empty. Each service's outbox keeps its published events, so what the other services missed is **replayed** by marking
those outbox rows unpublished again; the relay sends them, and every consumer ignores an event it has already handled (the
audit ledger by event id, the read model by its checkpoint table). This was tried on the local stack: 20 published events of
`cns_cmd` were marked unpublished, the relay re-sent all 20, and the ledger and the read model were exactly as before.

```sql
-- in each schema with an outbox (df_reg, cns_cmd, bil and dsr have one): replay what was published since a time
update <schema>.outbox_evt set published_at = null where published_at >= :since;
```

Replay the oldest outbox first if order across services matters to you; within a service the relay keeps order.

## Keys: what a database restore does not return

| Secret | If it is lost |
|---|---|
| `CNS_TKN_JWKS` (consent token signing keys) | Every consent token already issued stops verifying until fiduciaries refresh them (ADR-006); issue a new key set |
| `CNS_RCPT_JWKS` (receipt signing keys) | Receipts already issued can no longer be verified **unless the public keys are published elsewhere**. Keep the retired public keys; never throw one away |
| `DFREG_SECRETS_KEY` | Every API key's signing secret is unreadable: the keys must be reissued (each fiduciary revokes and issues again) |
| `CNSCMD_SECRETS_KEY` | Every webhook endpoint's signing secret is unreadable: endpoints must be recreated |
| `NTF_EMAIL_FINGERPRINT_KEY` | Principals' "stop request emails" choices stop applying until they save again (ADR-066) |

They are in the cloud's secret store (Secrets Manager, Key Vault, Secret Manager), which versions them. **On AWS this Terraform
creates the secret in the primary region only: it is not replicated to the recovery region, so losing the region loses the keys
unless an operator has an export.** **Back up the secret store's contents on a different footing from the database:** an operator
export, encrypted to the platform owner's key and kept offline, since the database backup and the keys that read it should not be
lost together.

## After any restore

1. **Services up, migrations clean.** Each service runs its Flyway migrations on start; a restore from an older backup is migrated
   forward. A service that refuses to start because the database is *ahead* of it means an older release is deployed: deploy the
   release the data belongs to.
2. **The ledger.** CM Admin, *Audit*, **Verify** (or `GET /api/aud/chain/verify`): the chain and the checkpoints are intact. If a
   restore went to a point before the last checkpoint was **anchored** externally, the ledger will look as if it had been
   shortened: that is the loss, not an attack, and it is recorded in the incident.
3. **Replay the outboxes** (E) from the time of the backup, so the read model and the ledger catch up with what the services hold.
4. **Counters.** Redis starts empty: usage for the current month restarts from zero and there is no tool yet to correct it from the ledger.
5. **Sign in** as staff and as a test fiduciary, raise a test consent request and approve it, and check its receipt verifies.
6. **Tell people.** A restore to an earlier time means some consents, withdrawals or requests made since are not in the data. The
   audit trail of the services' logs and of the cloud's access logs says which. A principal who withdrew a consent that the
   restore brought back must have that withdrawal made again, and soon: it is their right and a legal duty.

### What a restore can undo that it must not

- **Erasures.** A principal who closed their account (ADR-059) and a fiduciary's records purged after their retention (ADR-064) are
  *deleted*. A backup taken before that has them. **Restoring such a backup brings the data back.** The `aud.erased_subject` table
  and the `dsr` erasure records are in the same database, so a restore to a point before an erasure also forgets that it
  happened. **Until the erasures and purges are also written somewhere outside the database, the only protection is a procedure:**
  after any restore, list the erasures and purges made since the backup from the services' logs and the audit trail of the
  *current* system (before restoring over it), and run them again. The deletion log is a gap, listed under "Not yet".
- **Legal holds** are in `df_reg.df`; a restore to before a hold was placed lets the purge delete what was meant to be kept. Check
  every active hold after a restore.

## Pulsar's retention, for reference

The platform's own namespaces (`cm/dfreg`, `cm/cnscmd`, `cm/cnsqry`, `cm/ntf`, `cm/bil`, `cm/stf`, `cm/dsr`, and `cm-sbx/*` for the
sandbox) keep Pulsar's default: a message is deleted once every subscription has acknowledged it. That is deliberate, because the
outboxes are the record, not Pulsar. Only notification-service's namespace has a retention set (`init.pulsar.retention`: 60
minutes or 1 GB). A subscription that is down accumulates a backlog on the broker's disk; if a consumer is down for long enough to
matter, replay (E) rather than enlarge the broker.

## Not yet

- **Replicating the secrets** to the recovery region (the AWS secret is in the primary region only; check Azure and GCP).
- A scheduled backup job in the chart, and an alert when it or the drill did not run.
- The Prometheus alert rules the Terraform comment mentions.
- A **log of erasures and purges outside the database**, so a restore can re-apply them automatically.
- An end-to-end rehearsal of region loss on a real cloud, with the timings above measured.
- Backups of Pulsar's metadata (not needed to recover data, needed to recover subscriptions' positions), and of the notification
  database.
- A tested procedure for a **corrupted ledger entry** (as opposed to a lost one): the chain says where it breaks; what to do about
  it is a decision for the platform owner and, probably, a regulator.
