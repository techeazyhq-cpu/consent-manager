# ADR-097: The logical backup runs on a schedule, in the chart

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #101 (backup scheduling for several servers), #102
- **Builds on:** [ADR-074](adr-074-database-per-service.md), `docs/runbooks/backup-and-recovery.md`

## Context

The logical dump (`deploy/backup/postgres-backup.sh`) is the copy that survives the loss of the cloud account's backups, and the
restore drill checks it. Terraform made the bucket, the identity and its policy, but nothing ran the script: the runbook said "whoever
owns operations has to schedule it". With a database server per service (ADR-074 to ADR-096) there are several servers to dump, each
with its own address and password, which is the part nobody would get right by hand.

## Decision

1. **A CronJob in the Helm chart,** off by default (`logicalBackup.enabled`), daily at 01:00 (`schedule`), `concurrencyPolicy: Forbid`,
   a two-hour deadline, no retry beyond one.
2. **One dump step per database server,** as init containers run in order: the platform's own server (`consent_manager` and `keycloak`,
   or `logicalBackup.databases`) and every service that has a `database.host` of its own. Each uses that server's owner login and password
   (the service's `database.user` and `passwordKey`, else the owner's), the chart's TLS settings (ADR-085), and a manifest prefix of its
   own (`shared`, `cm-aud-svc`), as the script's `BACKUP_MANIFEST_PREFIX` was made for. The script is the one in `deploy/backup`, kept
   identical in the chart by a CI diff.
3. **One upload step after them,** with the cloud's own CLI image (`aws s3 cp --sse aws:kms`, `az storage blob upload`,
   `gcloud storage cp`) under the pod identity Terraform already made for the service account `cm-logical-backup`, to
   `logicalBackup.destination`. The chart creates that service account, with the annotations an identity needs. `provider: custom`
   takes an image and a shell snippet for anything else. **A failed dump fails the Job before anything is uploaded from that run,** so
   a bucket never holds a manifest that says `complete: false` as the day's backup.
4. **Seen by kube-state-metrics, not a textfile:** `LogicalBackupJobFailed` (page: a Job of the backup failed) and
   `LogicalBackupCronJobNotSucceeding` (page: no success for 36 hours). Both are unit-tested with promtool, which CI runs (ADR-092).
5. **Non-root (999), read-only root, no capabilities,** a scratch volume (20 Gi, `scratchSize`) that is not the node's disk, and a pod
   that carries the release's `part-of` label so the egress policy that opens the databases and the public storage endpoint applies.

## Checked

The chart renders for AWS, Azure and GCP and passes kubeconform; one dump step per server appears (the shared one, and a service with a
server of its own); off by default nothing renders; without a provider or destination it refuses to render. The dump step was run as
the pod runs it (postgres:16, uid 999, read-only root, all capabilities dropped, the script mounted read-only) against the local
database: both databases dumped, verified and a manifest written, exit 0. The two rules pass promtool's unit tests.

## Not yet / limits

- **Not run in a cluster, and the three uploads were never run.** The upload commands follow each CLI's documented way to use a pod
  identity (the Azure one logs in with the federated token), but no bucket, storage account or identity was used here; the first real
  run on each cloud will show a wrong image tag, a missing permission or a CLI flag.
- **The Azure and GCP pod identities need their annotations and label set in the values** (`serviceAccount.annotations`, `podLabels`):
  Terraform makes the identities, but its `helm_values` output does not yet fill these in.
- **A backup that never ran cannot be seen:** `kube_cronjob_status_last_successful_time` does not exist until the first success, so
  the alert is silent for a CronJob that has never succeeded; the failed-Job alert catches the case where it runs and fails.
- **The restore drill is not scheduled** and neither is its alert's source (`postgres-recovery.rules.yml` reads a textfile that a
  cluster does not have).
- **A dump of a large database is bounded by the scratch volume and the deadline:** 123 MB took seconds here; a terabyte will not fit
  in 20 Gi in two hours, and the cloud's own snapshots (which also run) are the answer for that size.
- **A dump uses the owner's login,** so the pod can read every table: the backup is as sensitive as the database.
- **Dumps are streamed through the pod,** not encrypted by it; the bucket's own encryption and the in-transit TLS are what protect them.
