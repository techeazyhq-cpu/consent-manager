# ADR-085: Database connections that check who answers

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #99 (TLS to the shared datastores)

## Context

The cloud values connect to PostgreSQL with `sslmode=require` (JDBC) and `sslMode=require` (R2DBC). `require` encrypts the
connection but accepts any certificate, so anything able to answer at the database's address, such as a spoofed DNS answer or a
host that took over a stale address, is trusted with every query, every password and every principal's data. The Terraform
outputs already said "verify-full, with the CA bundle", but the chart had no way to supply the bundle, so its own comment sent the
operator to "extraEnv/volumes in a hardened setup" that did not exist.

## Decision

1. **`config.database.tls`** in the Helm values: `mode` (`require`, `verify-ca` or `verify-full`; `verify-full` is the one to use,
   since it also checks the server's name), `caConfigMap` (a ConfigMap holding the server's certificate authority bundle: Amazon's
   RDS bundle, Azure's, Cloud SQL's) and `caKey`. It can be set for one service under its `database:` block as well.
2. **The chart then builds both connection strings and mounts the bundle:** `sslmode=verify-full&sslrootcert=/etc/db-ca/ca-bundle.pem`
   for Flyway (JDBC) and `sslMode=VERIFY_FULL&sslRootCert=...` for R2DBC (their option names and spellings differ), a read-only
   mount of the ConfigMap in every pod that has a schema, and the same for the `database-init` job (`PGSSLMODE`, `PGSSLROOTCERT`).
   With no `mode`, the chart uses `jdbcParams` and `r2dbcParams` exactly as before: nothing changes unless it is asked for.
3. **The Terraform outputs now name the same path** (`/etc/db-ca/ca-bundle.pem`, as Azure's and GCP's already did) and point at the
   chart setting instead of leaving the mounting to the operator.
4. **Checked against the real drivers,** not only rendered: a throwaway PostgreSQL with TLS and its own CA, and the audit service's
   image configured the way the chart now does. With `verify-full` and the CA, Flyway migrated and the service reported healthy;
   reached by a name the certificate does not cover, the connection was refused; with no CA, Flyway refused it; and with the CA
   given to Flyway but not to R2DBC, the service reported itself unhealthy (503). So each driver enforces what it is given.
5. **CI renders it** (`verify-full` with a bundle: seven services with both strings correct, and the bundle mounted in seven services and
   the init job).

## Not yet / limits

- **Off by default.** The cloud values still say `require`, because `verify-full` needs the ConfigMap to exist before the pods start.
  The comment in `values-aws.yaml` gives the setting and where Amazon publishes the bundle; making it the default is a change for
  when a deployment has the ConfigMap in its install steps.
- **Keycloak's database connection is the common chart's** and is not changed here; nor are Redis and Pulsar. Redis is `ssl: true` and
  trusts the usual public authorities, which is what ElastiCache's certificate chains to; Pulsar and Postgres inside the cluster
  (the development layout) have no TLS.
- **The bundle is not rotated by the chart.** When the provider rotates its authority, the ConfigMap is updated and the pods
  restarted.
- **`verify-ca` does not check the name,** so it is for a server whose certificate does not carry the name used (Cloud SQL's
  per-instance certificates); the ADR's recommendation is `verify-full` wherever it works.

## Options considered

- **Baking the bundle into the images:** ties every image to one cloud's authority and to its rotation.
- **A trust store holding the bundle, shared by the whole JVM:** would also trust that authority for everything else the service
  calls.
- **Leaving it to `extraEnv` and `volumes` values:** what the comment said; nothing in the chart consumed them.
