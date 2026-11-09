# ADR-095: A PostgreSQL server of its own per service on Azure

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #101
- **Builds on:** [ADR-074](adr-074-database-per-service.md) (AWS had this, Azure and GCP did not)

## Context

ADR-074 made it possible to point a service at a database server of its own and shipped Terraform for it on AWS only. On Azure the
runbook said "create the server by hand", which is where protections get left out: the shared server is zone-redundant, private, locked
against deletion and geo-backed-up, and a hand-made one is whatever the person remembered.

## Decision

1. **`dedicated_databases`** in the Azure datastores module (and the production root), keyed by the service's name in the Helm chart,
   with `db_name`, `username` and optionally `sku_name` and `storage_mb`. Empty by default: nothing is created until a service is asked for.
2. **Each service gets a Flexible Server with the shared server's protections:** zone-redundant high availability, no public access,
   in the same delegated subnet and private DNS zone, TLS 1.2 or later required, the same server settings (slow-query and
   idle-in-transaction limits, connection logging), geo-redundant backups, and a `CanNotDelete` lock. A Burstable SKU, which cannot
   have high availability, is refused at plan time.
3. **Its password is its own:** generated as an ephemeral value, never in state, written to the key vault as `<service>-db-password`
   (write-only), and named in the `postgres_dedicated` output next to the address, port, database and user.
4. **The production root emits the service's `database:` block** in `helm_values`, as on AWS, with `passwordKey` `<SERVICE>_DB_PASSWORD`;
   the operator syncs the vault secret into the application secret under that key.
5. **Checked by plan tests** (mocked provider): the server, database, six settings, lock and secret exist per service; the protections above
   hold; a Burstable SKU is refused; by default nothing exists. `terraform fmt`, `validate` and all twelve tests pass.

## Not yet / limits

- **Not applied to a real subscription.** The plan was checked against a mocked provider only; a real apply could reveal a quota, a
  delegated-subnet size (one subnet holds every server) or a naming limit the mock does not.
- **No metric alerts or health alert for the extra servers:** the storage and server-down alerts of the shared server (`alerts.tf`) are
  not extended to them yet, so a dedicated server is not watched by Azure Monitor until they are.
- **Private DNS zone and subnet are shared,** so the servers are separated by their own login, not by network.
- **GCP still has none.** Cloud SQL would need the same module work.
- **The owner is still one login per server** (ADR-082's per-schema owners are separate), and the data is still moved by the runbook's
  copy, not under live traffic.
