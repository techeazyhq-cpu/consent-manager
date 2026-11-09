# ADR-100: Keycloak verifies its database server

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #99
- **Builds on:** [ADR-085](adr-085-database-tls-verification.md) (the services' own connections)

## Context

ADR-085 made the services' database connections verify the server's certificate against a CA bundle. Keycloak, which runs from the
common chart and keeps its own database, was left out: its only setting was a free-text `keycloak.database.params` appended to the
JDBC URL, so `sslmode=require` encrypted the connection but did not check who was on the other end, and a CA bundle had no place to
go. Keycloak holds the realms, the users and the signing keys, so its database connection matters as much as any.

## Decision

1. **`keycloak.database.tls`** in the common chart, with the same shape as the services' `config.database.tls`: `mode` (`require`,
   `verify-ca` or `verify-full`) and `caConfigMap` (and `caKey`, default `ca-bundle.pem`). When set it builds the JDBC options
   (`sslmode=...&sslrootcert=/etc/db-ca/ca-bundle.pem`) and mounts the bundle read-only in the Keycloak pod.
2. **Unset, nothing changes:** the plain URL, or whatever `params` says, exactly as before. `params` still works for a deployment that
   wants a free-text option.
3. **The ConfigMap** is the same bundle the consent-manager chart uses, copied into the common chart's namespace (a ConfigMap cannot be
   read across namespaces).

## Checked

- **Render:** with nothing set, with `params`, with `tls.mode=require`, and with `verify-full` and a ConfigMap: the URL and the mount are
  as above in each case; `helm lint` and kubeconform pass.
- **Live, with the URL and the mount the chart produces,** Keycloak 25 (the image the local stack uses, in production mode as the chart
  starts it) against a PostgreSQL 16 with TLS on and a certificate for the name `pg-tls` from a CA made for the test: with
  `sslmode=verify-full&sslrootcert=...` and the CA mounted, it started, created its schema (94 tables) and PostgreSQL showed its
  sessions as TLS. With `verify-full` and no CA it could not open its database (`Could not open SSL root certificate file`).

## Not yet / limits

- **A wrong-name or untrusted-CA certificate was not tried,** only the missing CA; the driver's `verify-full` is what refuses those.
- **The CA ConfigMap is made by hand** in the common namespace; Terraform does not do it.
- **Keycloak 25's own JDBC driver and settings were used as the chart sets them;** a Keycloak upgrade is checked by the same test.
- **A rotated server CA** needs the ConfigMap updated and Keycloak restarted.
- **Keycloak's cache (Infinispan) and its other connections are not covered:** this is its database connection only.
