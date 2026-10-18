# ADR-035: A Keycloak realm per audience: data principals, fiduciaries, platform staff

- **Status:** Accepted; supersedes the "one realm per cell" choice in [ADR-033](adr-033-fiduciary-staff-accounts-separate-from-api-clients.md)
  (options considered) and refines [ADR-018](adr-018-regional-cells-for-data-residency.md) (each cell runs three realms, not one)
- **Date:** 2026-10-18

## Context

All three audiences sign in to one realm, `cm-realm`:

- data principals (DP portal)
- people at fiduciaries and their API clients (DF Console, the integration API)
- platform staff (CM Admin)

Two problems follow.

1. **One person per browser.** A Keycloak realm keeps one signed-in user per browser session. Signing in to the DP
   portal as one person makes the DF Console and CM Admin see that same person, refuse them, and offer only to sign
   out of everything. Keycloak refuses to switch users within a session by design ("different user already
   authenticated"), so no app-side change can fix this. It hurts testing, demos, and anyone who is both a data
   principal and someone's staff.
2. **Self-registration shares a realm with administrators.** The sign-in step registers anyone who enters an email
   address as a data principal (the DP portal needs this), and it runs on every client's sign-in page, CM Admin's
   included. So anyone can create an account in the realm that issues `CM_ADMIN` tokens. One misassigned role, or a
   bug in role mapping, would turn a self-registered account into staff.

## Decision

1. **Three realms per cell, one per audience:**

   | Realm | Who | Clients | Roles and claims | Sign-up |
   |---|---|---|---|---|
   | `cm-dp` | Data principals | `cm-dp-portal` | `DP` | Yes: a new address becomes a data principal |
   | `cm-df` | Fiduciaries' people and their systems | `cm-df-app`, `df-api-<id>` | `DF`, `DF_*`, `df_id` | No: people are invited (ADR-033), API clients are provisioned |
   | `cm-staff` | Platform administrators and operators | `cm-cm-admin` | `CM_ADMIN`, `CM_OPERATOR` | No: administrators add operators |

   Each realm has its own sessions, so a browser can be signed in to all three apps as three different people.
2. **Services trust each realm only for its own audience.** Every service accepts tokens from the three realms,
   picking the key set by the token's issuer. The `AuthCtx` built from a token then keeps only what that realm may
   assert:
   - staff roles only from `cm-staff`
   - fiduciary roles and `df_id` only from `cm-df`
   - `DP` and the data principal id only from `cm-dp`

   A token from the wrong realm loses those roles rather than being honoured. Role checks in the gateway and services
   see the filtered roles.
3. **Provisioning follows the audience:**
   - cm-df-reg-svc creates API clients and members in `cm-df`.
   - cm-stf-svc manages staff in `cm-staff`.
   - The sign-in plugin's auto-registration is configured per realm: `DP` in `cm-dp`, off elsewhere.
4. **Same sign-in experience.** All three realms use the same one-time-code flow (cm-idn-otp-spi) and each audience's
   login theme.

## Options considered

- **Separate Keycloak hosts for local testing only:** point some apps at `127.0.0.1:8080`, so the browser keeps
  separate cookies. It fixes the testing annoyance, but not self-registration sharing a realm with administrators,
  and production would still have one session per browser.
- **Keep one realm and explain it on the page:** no isolation, and a confusing experience for anyone with two roles.
- **Separate Keycloak instances per audience:** strongest isolation, but triple the operations for little gain over
  realms, which Keycloak already isolates (keys, sessions, users, flows).

## Consequences

- One browser can hold a data principal, a fiduciary's Owner and an administrator at once.
- A self-registered account can never hold a staff or fiduciary role; the realm boundary and the issuer check both stand
  in the way.
- Three realms to configure per cell, kept as three realm exports. Services carry a small issuer-to-realm map.
- **Migration:** accounts in `cm-realm` move to their audience's realm.
  - Locally, the realm exports import on Keycloak's next start and the seed users come with them.
  - Existing local data that refers to old user ids (a data principal's links, members) starts afresh.
  - In a deployed cell, users would be exported from `cm-realm` per role and imported, keeping their ids, before
    switching clients over.

## Compliance impact

- Stronger separation of duties and least privilege (ISO/IEC 27001 A.5.15, A.8.2; DPDPA reasonable security
  safeguards, s.8(5)): the population that can sign itself up is isolated from the population that administers the
  platform.
- Audit stays per person (ADR-033); each realm's events identify the audience.

## Follow-ups

- MFA for `cm-staff` and `cm-df` (ADR-033), now configurable per realm without affecting data principals.
- Optional federation to a fiduciary's own identity provider, in `cm-df` only.
