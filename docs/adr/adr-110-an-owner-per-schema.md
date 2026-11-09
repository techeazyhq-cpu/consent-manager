# ADR-110: Each schema has an owner of its own

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #101
- **Completes:** the "owner is still the shared one" limit of [ADR-082](adr-082-least-privilege-database-logins.md)

## Context

ADR-082 gave each service a login that can only read and write its own schema. The migrations still ran as one login that **owns every schema**: a
flaw in one service's migration (or one service's credential for them) could still drop or rewrite the tables of all the others. ADR-082
said giving each schema its own owner was "the same change again, one level up".

## Decision

1. **An owner per schema:** `cm_<schema>_owner`, a login with a password of its own, no superuser, no right to create databases or roles. It owns its
   schema and everything in it, and has no right on anyone else's. Each service runs its migrations (Flyway) as its owner, and its queries as
   `cm_<schema>_app` (ADR-082).
2. **`deploy/database/create-schema-owners.sh`** makes them. Run **before** the first migration it creates the schema, owned by its owner; run
   **later** it hands an existing schema over with every table, sequence, view, function and type in it. Safe to repeat. It runs as an
   administrator (the login that owns the schemas today, or the cloud's master user).
3. **The logins' default privileges follow the owner:** `create-runtime-roles.sh` takes `PER_SCHEMA_OWNERS=1`, so the tables a later migration
   creates, as `cm_<schema>_owner`, are readable and writable by `cm_<schema>_app` and by nobody else.
4. **Helm: `config.database.leastPrivilege.perSchemaOwners`,** on top of `enabled`. Each service's migration user and password become
   `cm_<schema>_owner` and the secret key `OWNER_DB_PASSWORD_<SCHEMA>`; a hook **before** install and upgrade runs the script (so the schemas exist and
   belong to their owners when each service first migrates), and the existing hook after it makes the logins. A service with its own
   `database.user` keeps it. Off by default.
5. **Locally:** `bash scripts/least-privilege.sh up-owners` does it for the local stack, as `up` did for the logins.
6. **Each owner can create schemas** (CREATE on the database), because each service's first migration begins `create schema if not exists`, and
   PostgreSQL checks the right to create a schema before it checks whether it exists. An owner that creates a schema owns that one and
   touches nothing of anyone else's. The earlier migrations cannot be edited without changing their checksums.

## Checked

- **Handing over a real database:** on the local database, every schema and all their objects moved from `cm` to their owners (61 tables and
  sequences and the 4 trigger functions, none left with `cm`; the database has no enum or domain types, so that part of the script has not been exercised), and all seven services validated their migrations and started as their owners, and ran
  their queries as their `_app` logins.
- **What an owner cannot do** (as `cm_aud_owner`): create, drop, alter, truncate or read in another schema (`permission denied for schema`),
  create a database or role; and it can create and drop a table in its own schema.
- **A fresh install:** on an empty database, the script created the schema owned by `cm_bil_owner`, the real billing image then ran all its migrations as that
  owner, and the 13 tables and sequences it made are the owner's. A table created later by the owner was readable and writable by the runtime login
  through the default privileges, and the runtime login could not change it or read the migration history.
- **The Helm hook as a pod** (postgres image, uid 999, read-only root, no capabilities): it made the owners and the schemas, ran again unchanged, and said
  plainly when a password was missing. The chart renders with the switch on (seven owners, seven logins, the hook before install and upgrade) and off
  (nothing changes), and kubeconform passes.
- **End to end** on the local stack with the services running that way: the leaving, dashboard, invoicing and processors scripts pass.
  `e2e-erasure` could not run: its fiduciary had used its monthly request allowance from the day's other runs (a quota 429, not a permission).

## Not yet / limits

- **Not run on a cluster or a managed database.** On Amazon RDS, Azure and Cloud SQL the administrator is not a superuser, so handing a schema over
  relies on it being made a member of each owner (the script does that) and on granting through that membership; this was run only with a
  local superuser. A managed service may also restrict `CREATE` on the database or role creation in ways the local one does not.
- **An owner can still create schemas,** decision 6: it cannot touch another's, but it is not as tight as an owner of one schema and nothing else.
- **The administrator remains all-powerful:** the login that runs the script (and the database's master user) can do anything; this limits
  the *services*, not the operator.
- **Backups, the restore drill and the logical dump** still run as the administrator (ADR-097), as does `split-schema.sh`.
- **Rotating an owner's password** is running the script with a new `OWNER_PASSWORD_<SCHEMA>`, updating the secret and restarting the service.
- **A new service** needs its schema added to the script's list (the chart reads the services' schemas, the local scripts name them).
- **A dedicated server per service (ADR-074)** keeps its own `database:` block and its own user; this does not apply there.
