# Runbook: per-service database logins

[ADR-082](../adr/adr-082-least-privilege-database-logins.md). Each service connects as a login that can only read and write
its own schema; only its migrations run as the owner.

## Locally

```bash
bash scripts/least-privilege.sh up      # makes the logins, restarts the seven database services as them
bash scripts/least-privilege.sh up-owners  # as up, and each schema gets an owner of its own first (below)
bash scripts/least-privilege.sh down    # back to the shared owner for everything
```

Check: `docker exec -e PGPASSWORD=cm common-postgres-1 psql -U cm -d consent_manager -c "select usename, count(*) from pg_stat_activity where datname = 'consent_manager' group by 1"`
shows `cm_<schema>_app` for each service and `cm` only for the migrations. If the gateway then answers "API key could not be checked", it
holds the old container addresses: restart it.

## Moving a deployment to it with the chart (one switch)

Put each login's password in the release's secret as `RUNTIME_DB_PASSWORD_DF_REG`, `..._CNS_CMD`, `..._CNS_QRY`, `..._NTF`, `..._BIL`,
`..._AUD` and `..._DSR`, then set:

```yaml
config:
  database:
    leastPrivilege: {enabled: true}
```

and install or upgrade. Every service queries as `cm_<schema>_app` and migrates as the owner, and a Job after the release makes the
logins (waiting for the schemas on a first install). The Job's log says each schema it did; if it says a schema does not exist, that
service has not started yet or its migrations failed: look at that service first. A service on a server of its own (ADR-074) sets its
own `database:` block and keeps the manual way below.

## An owner for each schema ([ADR-110](../adr/adr-110-an-owner-per-schema.md))

The logins above stop a service's *queries* reaching another schema, but the migrations still run as one owner of every schema. With an owner
per schema, each service migrates as `cm_<schema>_owner`, which owns its schema and has no right on another. Add the passwords to the secret as
`OWNER_DB_PASSWORD_DF_REG`, `..._CNS_CMD`, `..._CNS_QRY`, `..._NTF`, `..._BIL`, `..._AUD` and `..._DSR`, and set:

```yaml
config:
  database:
    leastPrivilege: {enabled: true, perSchemaOwners: true}
```

A job **before** the install or upgrade makes the owners and hands them the schemas (creating a schema that does not exist yet, or moving an
existing one, with every table in it, from the shared owner); the usual job after it makes the logins. It is safe to repeat. To check, as the
administrator: `select nspname, pg_get_userbyid(nspowner) from pg_namespace where nspname in ('df_reg','cns_cmd','cns_qry','ntf','bil','aud','dsr')`
shows `cm_<schema>_owner`, and as one owner, `create table other_schema.x (id int)` must say `permission denied for schema`. By hand:
`deploy/database/create-schema-owners.sh` with `OWNER_PASSWORD_<SCHEMA>`, then `create-runtime-roles.sh` with `PER_SCHEMA_OWNERS=1`.
Each owner can create schemas of its own (its first migration needs it), and the administrator is unchanged: this limits the services.

## Moving a deployment to it by hand

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
- A **new schema** (a new service): run the script for it (`create-runtime-roles.sh newschema` with `RUNTIME_PASSWORD_NEWSCHEMA`), and, with an
  owner per schema, `create-schema-owners.sh newschema` first (with `OWNER_PASSWORD_NEWSCHEMA`).
- A service that says **permission denied** in its log: it wants something the login does not have (DDL at run time, a table in
  another schema). That is a bug in the service to fix, not a reason to widen the login.

## Rotating a password

Run the script again with the new `RUNTIME_PASSWORD_<SCHEMA>`, update the secret, restart that service. An owner's: the same with
`create-schema-owners.sh` and `OWNER_PASSWORD_<SCHEMA>`.
