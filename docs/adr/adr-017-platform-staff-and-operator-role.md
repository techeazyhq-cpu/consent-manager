# ADR-017: Platform staff in their own service, with an operator role

- **Status:** Accepted
- **Date:** 2026-10-11

## Context

Until now the platform had one kind of staff account, `CM_ADMIN`, seeded in the realm file. Running the platform
needs more people than its administrators: operators who onboard fiduciaries and watch how they are doing, without
being able to change what a fiduciary pays, suspend it, or add more staff. Somebody also has to create those
accounts, and each member of staff needs to see their own account and where they are signed in.

Sign-in is passwordless (ADR-008): an account is an email address, and a one-time code proves it at every sign-in.
There is no password to set or change.

Managing staff is not fiduciary registration: different people, different rules, different reasons to change. The
existing services each own one bounded context (ADR-001).

## Decision

1. **A `CM_OPERATOR` realm role** beside `CM_ADMIN`. The gateway lets operators:
   - read the tenant list, profiles, subscriptions and consent figures;
   - register and activate tenants, and set their logos.

   Changing a plan, suspending a tenant and managing staff stay with `CM_ADMIN`.
2. **A new service, `cm-stf-svc` (port 8087), for the platform staff context**, with the same clean-architecture
   modules as every other service (ADR-002). Keycloak is the system of record for staff accounts, so the service
   keeps no database. It reaches Keycloak's Admin REST API through the shared `cm-kc-kit` admin-token filter, behind
   the `keycloak-admin` circuit breaker (ADR-016).
3. **Rules, in the application layer:**
   - Only administrators add operators, list staff, and disable or enable an operator.
   - Disabling also signs the account out everywhere.
   - Nobody disables themselves or an administrator from here, so the platform can't be left without one.
   - An address that already has any account (staff, fiduciary user or data principal) is refused with 409, never
     turned into staff.
4. **Every member of staff has `/api/stf/me`** (their account), `/me/sessions` (where they are signed in) and
   `/me/sign-out-everywhere`: CM Admin's Profile and Sign-in & security pages. There is no "change password".
5. **The role is created on first use** if the realm lacks it, because a realm imported before the role existed is
   never re-imported. Fresh setups get it from the realm file, with `operator@cm.local` seeded beside
   `admin@cm.local`.
6. **No half-made accounts.** If giving a new user the operator role fails, the user is deleted again, so a retry can
   create it.

## Consequences

- Operators can do the day-to-day work without holding the power to change billing or staff.
- One more service to run: added to docker-compose and the gateway. CI builds it like the others.
- The service talks to Keycloak with the bootstrap admin account, as cm-df-reg-svc does. A dedicated service
  account scoped to `manage-users` is the hardening still to do (plan §16).
- Staff changes are not yet written to the audit ledger (ADR-014), which records consent events. Recording who added
  or disabled whom is follow-up work.
