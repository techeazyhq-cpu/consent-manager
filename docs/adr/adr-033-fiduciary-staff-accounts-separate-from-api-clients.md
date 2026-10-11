# ADR-033: Fiduciaries' people have their own accounts; API clients are for systems only

- **Status:** Proposed; supersedes decision 4 of [ADR-009](adr-009-independent-micro-frontends.md) (the DF app
  authenticates as the fiduciary's API client)
- **Date:** 2026-10-11
- **Design:** [product architecture §3, gap P1](../product/product-architecture.md)

## Context

The DF app signs in by asking a person to paste the fiduciary's API client id and secret into the browser, then runs
the client-credentials grant from the page (ADR-009 decision 4). This has four problems:

- a long-lived machine credential is exposed to the browser, its extensions and its history
- everyone at the fiduciary shares one identity, so no action can be traced to a person
- MFA and roles are impossible
- revoking one person's access means rotating the secret every system uses

A consent manager must be able to say who raised a request or changed a purpose, for its own audit and the Board's
(DPDPA First Schedule B12).

## Decision

1. **People sign in as themselves.** Fiduciary staff have Keycloak user accounts in the cell's realm.
   - Sign-in is by one-time code or passkey, with **MFA required** (passkey, or TOTP as fallback).
   - Optionally, sign-in is federated to the fiduciary's own IdP (OIDC or SAML), with the fiduciary's domain verified
     first.
   - Each account belongs to exactly one fiduciary (a `df_id` claim and a fiduciary role).
2. **Roles within a fiduciary:**

   | Role | May |
   |---|---|
   | Owner | Everything, including transferring ownership |
   | Administrator | Team, API clients, webhooks, organisation |
   | Consent manager | Purposes and notices, raising requests, the dashboard, relayed requests |
   | Developer | API clients and webhooks in the sandbox, read-only in production |
   | Auditor / DPO | Read-only: dashboard, records, receipts, relayed requests, the team's activity |

   Every staff action is written to the audit ledger with the person's id.
3. **API clients are for systems only.**
   - They are created in the Console by an Administrator, with scopes per area (requests, tokens, status, webhooks,
     relay).
   - Credentials: `private_key_jwt` is preferred (the fiduciary keeps the private key); client secrets are allowed,
     shown once and rotatable.
   - A fiduciary may run several clients, and revoke each one on its own.
   - API clients can't sign in to the Console, and people can't call the API with their own tokens beyond the Console's
     needs.
4. **Migration.** Activation stops showing a client secret meant for people. Existing fiduciaries invite their
   Owner, who invites the rest. The DF app's secret-paste sign-in is removed.

## Options considered

- **Keep the shared client secret, behind a proxy:** it would hide the secret from the browser, but people still
  share one identity and can't have MFA or roles.
- **Separate realms for fiduciary staff:** stronger isolation, but federation and the cell model (ADR-018) are simpler
  with one realm per cell and a fiduciary claim. Revisit if a regulator or customer requires it.

## Consequences

- Every Console action can be traced to a person, protected by MFA and limited by role. Systems use scoped, rotatable
  credentials.
- Keycloak gains fiduciary user management. Invitations go through notification-service.
- Fiduciaries must invite their people before the old sign-in is removed. That needs a short transition window.

## Compliance impact

- DPDPA First Schedule B7 (safeguards) and B12 (audit).
- HIPAA §164.312(a), (d) (unique user identification, person authentication).
- ISO/IEC 27001 access control.
- OWASP ASVS V2, V4.
