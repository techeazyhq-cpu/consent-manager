# ADR-082: A database login for each service that can only touch its own schema

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #101 (the per-service logins ADR-074 left open)

## Context

Every service connected to PostgreSQL as `cm`, the login that owns all the schemas and runs all the migrations. One flaw in
one service (an injection, a leaked connection string) was therefore a flaw in all of them: that login could read the consents
of every principal, rewrite the audit ledger, or drop any table. Schema-per-service (ADR-007) and the move to a database of
its own per service (ADR-074) kept data apart by convention; nothing in the database enforced it. Each service already took
its migration login (`SPRING_FLYWAY_USER`) and its query login (`SPRING_R2DBC_USERNAME`) separately, so separating them
needs no change to any service.

## Decision

1. **Each service has a login of its own** (`cm_<schema>_app`): it may `select`, `insert`, `update` and `delete` the rows of
   its own schema and use its sequences, and has no privilege on any other schema, no right to create, alter, drop or truncate
   anything, and no access to the migration history table. The login is a plain role: not a superuser, not able to create
   databases or roles.
2. **Migrations stay with the owner.** A service still runs Flyway as the schema owner at start-up. The roles script sets
   default privileges for the owner, so a table a later migration creates is usable by the service's login with no further step.
3. **A script makes the logins** (`deploy/database/create-runtime-roles.sh`): idempotent, passwords from one environment
   variable per schema (never on a command line, never printed), schema names checked. It is run by whoever owns the database,
   after the first migrations have created the schemas, and again when a schema is added.
4. **Helm separates them:** `config.database.migrationUser` and `migrationPasswordKey` (also per service under `database:`).
   Left out, both are the query login, exactly as before; nothing changes for a deployment that does not opt in.
5. **The local stack can run this way:** `bash scripts/least-privilege.sh up` makes the logins and restarts the seven database
   services as them (`infra/docker-compose.least-privilege.yml`, dev-only passwords).
6. **Tested** by running the stack that way and then the end-to-end scripts: processors, invoicing, insights, terms, export, signup,
   status, leaving, closing (including the retention purge and the deletion of a closed fiduciary's records), end of contract,
   suspension, notification preferences, the AI ingestion guard and the consent sidecar all pass. As one service's login, reading
   its own table works; reading another schema, creating a table, dropping a table, truncating a table and reading the
   migration history are each refused.

## Not yet / limits

- ~~**Not automated in Helm.**~~ Done: [ADR-090](adr-090-least-privilege-in-one-switch.md), one switch and a hook. As it was written: The roles must exist before a release switches to them, and the schemas must exist before the roles
  are granted (the first migrations create them). The runbook gives the order: install as the owner, run the script, upgrade with
  the split. A hook that does it is the next step.
- **The owner is still the shared `cm`** in the default configuration and in the local stack. Giving each schema its own owner
  is the same change again, one level up.
- **A compromised service can still do what the service does** with its own rows, and the audit ledger's own protections
  (append-only, hash chain: ADR-040) are what stop it rewriting that. This limits the blast radius to one schema.
- **Passwords are per schema, not rotated for you.** Rotating one is running the script with a new value, then updating the
  secret and restarting that service.
- **Services that read another's data over the database** would break. None do: each reaches the others by HTTP or Pulsar,
  which is what made this safe to do; a new cross-schema query is now refused rather than silently working.

## Options considered

- **Row-level security:** protects rows within a table, not tables from each other; the problem here is whole schemas.
- **A separate database per service from the start:** ADR-074 allows it, and then the login can only connect to its own database.
  Schemas in one database are the common case, and this is what makes them safe.
- **Revoking DDL from `cm` and migrating from a pipeline:** moves the privilege out of the running system entirely. Worth doing,
  and compatible with this; start-up migrations are what the services do today.
