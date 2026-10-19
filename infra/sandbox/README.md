# The sandbox

A copy of the Consent Manager API for fiduciaries to test against: the same images as production, with its own
database, Pulsar tenant, Keycloak realms (so its own users, keys and token issuers), consent token keys, PDP and
inbox. Locally it shares only the Postgres, Pulsar, Keycloak and Redis servers. The design is
[ADR-037](../../docs/adr/adr-037-running-the-sandbox.md).

## Run it

```bash
./gradlew build
docker compose -f infra/docker-compose.yml --profile sandbox up -d --build
```

Keycloak imports the `sbx-cm-*` realms the next time it starts. If it was already running, restart it:
`docker compose -f infra/docker-compose.yml restart keycloak`.

| What | Where |
|---|---|
| Gateway (the sandbox's API) | http://localhost:9100 |
| Inbox: every email the sandbox "sends" | http://localhost:8125 |
| Realms | `sbx-cm-df` (fiduciaries and their API clients), `sbx-cm-dp`, `sbx-cm-staff` |
| Platform staff | `admin@sandbox.example`, `operator@sandbox.example` in `sbx-cm-staff` |

**From the DF Console:** an Owner, Administrator or Developer opens **API playground** and presses **Get sandbox
keys**. The sandbox makes a copy of their fiduciary (same id, its name, nothing else) and hands back an API client for
it ([ADR-038](../../docs/adr/adr-038-api-playground-and-sandbox-keys.md)). Pressing it again replaces the secret.

**By hand:** a sandbox fiduciary is created and activated like a production one, through the sandbox's gateway with a
`sbx-cm-staff` token. Its API client lives in `sbx-cm-df` and gets tokens from
`http://localhost:8080/realms/sbx-cm-df/protocol/openid-connect/token`. Those tokens are refused by production, and
production's are refused here.

## Test contacts only

The sandbox asks only people who can't exist:

- email addresses at `sandbox.example`, for example `asha@sandbox.example`
- mobile numbers from `+44 7700 900000` to `+44 7700 900999`

Anything else is refused with 400 `not-a-test-contact`, and a batch marks such a row invalid with the reason.

## Reset

```bash
bash infra/sandbox/reset.sh
```

This empties the sandbox's database, topics, usage counters and inbox, and removes the fiduciaries' API clients and
members from `sbx-cm-df`. Production is not touched. Run it on a schedule (nightly is the default promise to sandbox
users).

## Files

| File | What |
|---|---|
| `make-realms.py` | Writes `infra/keycloak/realm-export/sbx-cm-*.json` from production's realms. Run it after changing those, and commit the result |
| `pg-init.sh` | Creates the `cm_sandbox` database once. Run by the `sbx-pg-init` container. The `cm-sbx` Pulsar tenant comes from `infra/pulsar/init-tenant.sh`, run by `sbx-pulsar-init` |
| `opa/data.json` | Points the sandbox's PDP at the sandbox's cm-cns-cmd-svc for consent token keys and live status |
| `reset.sh` | The reset |
