# ADR-037: Running the sandbox: the same images, its own stores, realms and keys, shared servers

- **Status:** Accepted
- **Date:** 2026-10-18
- **Implements:** [ADR-034](adr-034-sandbox-and-api-playground.md) §1

## Context

ADR-034 calls for a sandbox: the same images and contracts as production, deployed separately, with synthetic
principals only, no delivery, its own signing keys and issuer, no billing, and periodic resets. Fiduciaries test there,
and the DF Console's API playground (next) calls only the sandbox.

The forces:

- **Isolation is the point.** No sandbox token may verify in production, and the reverse. No sandbox event may reach a
  production consumer. No real person's contact may be stored or written to.
- **Same code.** A sandbox that runs different code proves nothing, so the difference must be configuration only.
- **Footprint.** Locally, the whole stack already fills Docker's memory on a 16 GB laptop. A second full copy (Pulsar,
  Keycloak, Postgres and eight services) adds about 4–5 GB.

## Decision

1. **The same images, a `sandbox` Spring profile.** Each sandboxed service runs with
   `SPRING_PROFILES_ACTIVE=docker,sandbox` and `CM_PULSAR_TENANT=cm-sbx`.
   - The sandbox runs gw, df-reg, cns-cmd, cns-qry, ntf and bil. It leaves out aud (no evidence is kept for test data)
     and stf (platform staff manage it from its own staff realm).
   - Its gateway is `:9100` and its inbox `:8125`. Production's gateway stays `:9000`.
2. **Its own stores on shared servers.** Postgres, Pulsar, Keycloak and Redis servers are shared locally to keep the
   footprint small. Nothing stored on them is shared:

   | What | Production | Sandbox |
   |---|---|---|
   | Database | `consent_manager` | `cm_sandbox` |
   | Pulsar tenant (every topic) | `cm` | `cm-sbx` |
   | Keycloak realms: users, clients, signing keys, issuers | `cm-dp`, `cm-df`, `cm-staff` | `sbx-cm-dp`, `sbx-cm-df`, `sbx-cm-staff` |
   | Redis (usage counters) | database 0 | database 1 |
   | Consent token keys | volume `cns-tkn-keys` | volume `sbx-cns-tkn-keys` |
   | PDP (checks consent tokens against keys) | `opa` | `sbx-opa` |
   | Mail | notification-service, or Mailpit `:8025` | Mailpit `sbx-mailpit` `:8125`, never delivered |

   In production the sandbox is its own deployment with these same settings, so it shares nothing at all.
3. **Tokens never cross.**
   - `cm.security.realm-prefix: sbx-` makes every service trust only the `sbx-` realms' issuers and keys.
   - The realms are generated from production's by `infra/sandbox/make-realms.py`, so they don't drift. The exports
     hold no keys: Keycloak makes new ones for each realm.
   - Consent tokens are signed with the sandbox's own keys and checked by the sandbox's own PDP.
4. **Only test contacts.** With `cm.sandbox.enabled`, cm-cns-cmd-svc refuses any contact that could be a real person's
   (400 `not-a-test-contact`; a batch marks the row invalid with the reason). It accepts:
   - addresses at `sandbox.example` (`.example` is reserved by RFC 2606)
   - numbers `+44 7700 900000`–`900999` (kept by Ofcom for drama, never assigned)
5. **No delivery, no billing.**
   - cm-ntf-svc refuses to start in the sandbox with any provider but `local` (Mailpit and the in-memory catchers).
   - cm-bil-svc runs the same plans, so a sandbox fiduciary meets quotas and 429s as it would in production. It never
     charges: bil holds quotas and usage only.
6. **Reset.** `infra/sandbox/reset.sh` empties the sandbox's database, Pulsar topics, usage counters and inbox, and
   removes `sbx-cm-df`'s API clients and fiduciary members. Accounts in the other sandbox realms stay. It touches only
   sandbox stores. Scheduling is the operator's choice; nightly is the default promise.

## Options considered

- **A full second stack** (its own Postgres, Pulsar, Keycloak, Redis): no code changes, and the closest to a separate
  production deployment. Too heavy locally: Docker already ran out of memory with one stack.
- **A test mode inside production** (flagged rows, test keys): rejected in ADR-034. Synthetic and real data would share
  stores, keys, retention and backups.

## Consequences

- One codebase and one set of images, with two small, testable switches: the realm prefix and the Pulsar tenant.
- About 1.6 GB more locally (six services at 256 MB heaps, plus OPA and Mailpit). Pulsar's standalone heap is capped
  at 512 MB to make room, which development traffic doesn't miss.
- Sharing servers locally means a server outage takes both down. That's acceptable on a laptop and doesn't happen in
  production, where the deployments are separate.
- The Pulsar tenant is read from the environment when the process starts, not from Spring configuration, because topic
  names are built in static code. It is validated, and an invalid name stops the process.
- **Not yet:** self-service sandbox accounts for developers (ADR-034 §4), the principal simulator, the webhook inspector
  and conformance checks. For now, platform staff create sandbox fiduciaries in `sbx-cm-staff`.

## Compliance impact

- Data minimisation: the sandbox can't hold a real person's contact or send them anything.
- Evidence and retention duties don't apply to synthetic data, so the sandbox keeps no audit ledger, and resets.
