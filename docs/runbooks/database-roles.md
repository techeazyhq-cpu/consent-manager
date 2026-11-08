# Runbook: per-service database logins

[ADR-082](../adr/adr-082-least-privilege-database-logins.md). Each service connects as a login that can only read and write
its own schema; only its migrations run as the owner.

## Locally

```bash
bash scripts/least-privilege.sh up      # makes the logins, restarts the seven database services as them
bash scripts/least-privilege.sh down    # back to the owner for everything
```

Check: `docker exec -e PGPASSWORD=cm common-postgres-1 psql -U cm -d consent_manager -c "select usename, count(*) from pg_stat_activity where datname = 'consent_manager' group by 1"`
shows `cm_<schema>_app` for each service and `cm` only for the migrations. If the gateway then answers "API key could not be checked", it
holds the old container addresses: restart it.

## Moving a deployment to it

The logins can be granted rights on a schema only once the schema exists, and a login needs the default privileges in place before the
next migration creates a table, so the order matters.

1. **Install or upgrade as today.** The first start creates the schemas and tables as the owner.
2. **Make the logins**, as the owner (or an administrator), with one password per schema in the environment:

   ```bash
   export RUNTIME_PASSWORD_DF_REG=... RUNTIME_PASSWORD_CNS_CMD=... RUNTIME_PASSWORD_CNS_QRY=... RUNTIME_PASSWORD_NTF=... \
          RUNTIME_PASSWORD_BIL=... RUNTIME_PASSWORD_AUD=... RUNTIME_PASSWORD_DSR=...
   PGHOST=... PGUSER=cm PGPASSWORD=... DB_NAME=consent_manager bash deploy/database/create-runtime-roles.sh
   ```

   Put each password in the release's secret under a key of its own (for example `CM_AUD_RUNTIME_DB_PASSWORD`).
3. **Upgrade the release with the split**, in the values:

   ```yaml
   config:
     database:
       migrationUser: cm           # every service migrates as the owner
       migrationPasswordKey: DB_PASSWORD
   services:
     cm-aud-svc:
       database: {user: cm_aud_app, passwordKey: CM_AUD_RUNTIME_DB_PASSWORD}
     # ... one block for each service with a schema
   ```

   Each service has its own `user` and `passwordKey`, since each login has a different name and password; the owner's stays in
   `DB_PASSWORD`.
4. **Check.** The service starts (Flyway ran as the owner), the end-to-end scripts pass, and `pg_stat_activity` shows the new logins.
   As one login, `select` on another schema must say `permission denied for schema`.

## When a migration or a new service needs something

- A **new table** in an existing schema needs nothing: default privileges cover it.
- A **new schema** (a new service): run the script for it (`create-runtime-roles.sh newschema` with `RUNTIME_PASSWORD_NEWSCHEMA`).
- A service that says **permission denied** in its log: it wants something the login does not have (DDL at run time, a table in
  another schema). That is a bug in the service to fix, not a reason to widen the login.

## Rotating a password

Run the script again with the new `RUNTIME_PASSWORD_<SCHEMA>`, update the secret, restart that service.
