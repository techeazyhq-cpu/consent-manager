# ADR-090: Least-privilege database logins from one Helm switch

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #101
- **Completes:** the first "Not yet" item of [ADR-082](adr-082-least-privilege-database-logins.md)

## Context

ADR-082 gave each service a login that can only touch its own schema, but turning it on in a deployment took a script run by hand, in
the right order (after the first migrations had created the schemas), and a values block for each of seven services naming its login
and its password key. That is a procedure a person can get wrong, and a first install could not do it at all: the logins need the
schemas, the schemas need the services to start, and the services (configured for their logins) cannot.

## Decision

1. **One switch,** `config.database.leastPrivilege.enabled`. When on, every service with a schema queries as `cm_<schema>_app` with
   the password from the secret key `RUNTIME_DB_PASSWORD_<SCHEMA>`, and still migrates as the owner. A service that sets its own
   `database.user` keeps it. Off (the default), nothing about the chart changes.
2. **A hook creates the logins** (a Job after install and upgrade) by running the same script as ADR-082, shipped inside the chart and
   kept identical to `deploy/database/create-runtime-roles.sh` by a CI diff. It runs as a non-root user on a read-only file system,
   takes the owner's password from the secret the migrations use and each login's password from its own key, uses the database's
   TLS settings (ADR-085), and has the script's ordinary passwords-never-printed behaviour.
3. **It waits for the schemas,** up to `waitForSchemasSeconds` (default 900), polling every five seconds, because on a first install the
   services' migrations create them while the hook runs. A schema that never appears stops it with exit 3 and a plain message; Helm
   retries the Job and fails the release if it cannot finish. So one `helm install` is enough, and a failed login creation is loud.
4. **It is safe to run again** on every upgrade: it sets the password, regrants, and changes nothing else (ADR-082).
5. **Checked:** the chart lints and passes kubeconform with the switch on; on, all seven services query as their own login and
   migrate as the owner, the Job gets each schema and its password key, a service with its own user keeps it; off, no Job and no
   change. And the hook was run as it runs in a cluster (the Postgres image, uid 999, read-only root file system, the script
   mounted read-only) against the local database: a missing schema waited and exited 3, and the seven schemas got their logins,
   one of which could read its own table and was refused another schema's.

## Not yet / limits

- **Not run on a cluster.** The Job is rendered and its script was run in the same kind of container, but the first real install with the
  switch on has not happened; a hook ordering problem (Helm waits for the release's resources before running a post-install hook only
  with `--wait`) would show there. Without `--wait` the hook runs as soon as resources are created, and its own wait for the schemas
  covers that.
- **The passwords must exist in the secret first** (`RUNTIME_DB_PASSWORD_<SCHEMA>` for each schema, in Terraform's secret or the
  external secret store). A missing key fails the pods and the Job at start, with the usual message.
- **Rotating** a password is changing the secret and running an upgrade (the hook sets it) and restarting the service.
- **The owner is still the shared one** and the Job is not per-database for a service on a server of its own (ADR-074): that service's
  `database:` block would need its own hook. A service with its own server keeps the ordinary procedure.
- **The hook needs the owner's password** and the right to create roles, which Amazon RDS's master login has.

## Options considered

- **Creating the logins in a migration:** a migration has no password to give them, and would put the choice in every service.
- **An init container in each service's pod:** seven copies of the same step, each needing the owner's password inside the service's pod.
- **A pre-install hook:** runs before the schemas exist, which is the thing that makes this hard.
