# ADR-008: Keycloak for identity, passwordless sign-in by one-time code

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1); passwordless sign-in 2026-10-10 (#151); a client and login theme per frontend
  2026-10-11

## Context

Three kinds of user sign in: Data Principals (anyone, often for the first time because a fiduciary asked for their
consent), fiduciary staff and systems, and platform administrators. Principals are identified by an email address or
phone number they already have; asking them to create and remember a password is a barrier and a liability.
Fiduciaries' systems call the API machine to machine. Identity is generic: building it is not where this product adds
value.

## Decision

1. **Keycloak, realm `cm-realm`**, imported from `infra/keycloak/realm-export/cm-realm.json`. Realm roles `CM_ADMIN`,
   `DF`, `DP` travel in the access token's `roles` claim; a principal's CM identity is their Keycloak subject.
2. **Each browser app has its own public client**, `cm-dp-portal`, `cm-df-app` and `cm-cm-admin`, with the
   authorization code flow and PKCE (S256), and its own login theme from `cm-idn-theme`: `cm-dp`, `cm-df` and
   `cm-admin` on a shared `cm-base`, which is also the realm default. Keycloak chooses a login theme per client, so
   one client per audience is what lets a data principal, a fiduciary and an administrator each see a sign-in page
   meant for them. Each micro-frontend runs its own session ([ADR-009](adr-009-independent-micro-frontends.md)).
   (Until 2026-10-11 the three apps shared one client, `cm-shell`.)
3. **Each fiduciary gets its own confidential client**, `df-api-<dfId>`, created by `cm-df-reg-svc` through the Admin
   REST API when the fiduciary is activated, with a hard-coded `df_id` claim. Fiduciary systems use the client
   credentials grant; services take `df_id` from the token, never from a parameter.
4. **Sign-in is passwordless.** The realm's browser flow is an SSO cookie, otherwise the `cm-idn-otp-spi`
   authenticator: an email address or E.164 phone number, then a 6-digit code delivered through `cm-ntf-svc`
   (5-minute validity, 5 wrong guesses per code, throttled resends, only a salted hash kept in the server-side
   authentication session, Keycloak brute-force protection across sign-ins). The page looks the same whether or not
   the address has an account.
5. **Signing in creates a principal account.** An unknown address that proves itself with a code becomes a `DP` user;
   the realm's user profile asks for nothing else, so neither a name nor an email is required (data minimisation).
   Password registration is off.

## Consequences

- No passwords to store, reset or leak for principals and administrators; the code channel (email or SMS) is the
  credential, so its security is the account's security.
- Codes are only as available as notification delivery; `cm-ntf-svc` sends them as OTP-category messages through
  notification-service, which delivers them first and never late
  ([ADR-010](adr-010-notification-delivery-through-notification-service.md)).
- `directAccessGrantsEnabled` remains on `cm-dp-portal` and `cm-cm-admin` for the scripted verification only, with
  the seeded demo users' passwords; it is to be turned off once the scripts sign in with codes.
- `cm-df-app` and its theme are ready, but the DF app does not use them yet: fiduciary staff still connect with their
  organisation's API client secret. Moving staff to browser sign-in needs a `df_id` per staff user.
- The browser flow is realm-wide, so signing in through any client can create a `DP` account, including on the CM
  Admin page. Binding a flow without auto-registration to `cm-cm-admin` would close that.
- The login themes are FreeMarker templates on Keycloak's unstyled `base` theme, so a Keycloak upgrade can change the
  `registrationLayout` contract they follow; `cm-idn-theme`'s tests and `scripts/verify-otp-login.mjs` catch drift.
- The realm file is applied only on first import; changing it needs a fresh import locally and an admin-API migration
  in a long-lived environment.
