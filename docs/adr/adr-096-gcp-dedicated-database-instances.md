# ADR-096: A Cloud SQL instance of its own per service on GCP, and Azure's extra servers watched

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #101
- **Builds on:** [ADR-074](adr-074-database-per-service.md), [ADR-095](adr-095-azure-dedicated-database-servers.md)

## Context

ADR-074 gave AWS a database server per service in Terraform and ADR-095 gave Azure the same. GCP was the last of the three where the
runbook said "create the server by hand". ADR-095 also left the Azure servers without the shared server's alerts.

## Decision

1. **`dedicated_databases` in the GCP datastores module and production root,** keyed by the service's name in the Helm chart, with
   `db_name`, `username` and optionally `tier` and `disk_size_gb`. Empty by default.
2. **Each service gets a Cloud SQL instance with the shared instance's protections:** regional high availability, private IP only,
   `ENCRYPTED_ONLY`, point-in-time recovery, 30 backups stored in the recovery region, deletion protection (and backups retained on
   delete), query insights and the same eight database flags (shared with the shared instance's list as one local).
3. **Its password is its own:** ephemeral (never in state), written write-only to a Secret Manager entry
   `<name>-<service>-db-password`, replicated to the primary and recovery regions only. The `postgres_dedicated` output names it.
4. **Its CA is its own.** Each Cloud SQL instance has a CA that signs only that instance, which is what makes `verify-ca` sound
   (ADR-042); so the output carries the instance's `server_ca` and the operator adds it to the `db-ca-bundle` ConfigMap, which holds
   several certificates. Without it a service's `verify-ca` connection to its own instance is refused, which is the safe failure.
5. **The production root emits the service's `database:` block** in `helm_values`, as on AWS and Azure.
6. **GCP's alert policies already cover the new instances,** because they match every Cloud SQL instance in the project by resource
   type, not one by name; checked by reading `alerts.tf`, not by a test that could tell.
7. **Azure's extra servers are watched too:** the storage and server-down alerts are made for the shared server and each dedicated one,
   and the health alert's scope lists them all. The shared server's two alerts keep their names and move to their new keys with
   `moved` blocks, so an existing deployment does not recreate them.
8. **Checked by plan tests** (mocked provider): GCP's instance, database, owner, secret and eight flags exist per service; its
   protections hold; nothing exists by default (9 tests). Azure's alerts per server, the shared alert's unchanged name and the
   renamed keys are tested (13 tests). `terraform fmt` and `validate` pass for AWS, Azure and GCP.

## Not yet / limits

- **Not applied to real projects or subscriptions,** on either cloud. The plans are against mocked providers only.
- **The `moved` blocks are not exercised:** a test has no prior state. They are the documented way to rename a key; the first plan on a
  real Azure deployment should show the two alerts as moved, not replaced, and that is the thing to read.
- **The Azure health alert's scope is not asserted** (a mocked provider gives every server the same id, which a set collapses), only
  validated.
- **GCP's alerts cover every Cloud SQL instance in the project,** including ones this repository did not create.
- **The data is still moved by the runbook's copy,** the owner is one login per server, and the servers share a network (the private
  services range), so the separation is by login and not by network.
