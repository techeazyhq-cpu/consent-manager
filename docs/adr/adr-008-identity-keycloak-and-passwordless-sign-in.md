# ADR-008: Keycloak for identity, passwordless sign-in by one-time code

- **Status:** Accepted
- **Date:** 2026-08-30 (Phase 1); passwordless sign-in 2026-10-10 (#151)

## Context

Three kinds of user sign in: Data Principals (anyone, often for the first time because a fiduciary asked for their
consent), fiduciary staff and systems, and platform administrators. Principals are identified by an email address or
phone number they already have; asking them to create and remember a password is a barrier and a liability.
Fiduciaries' systems call the API machine to machine. Identity is generic: building it is not where this product adds
value.

## Decision

1. **Keycloak, realm `cm-realm`**, imported from `infra/keycloak/realm-export/cm-realm.json`. Realm roles `CM_ADMIN`,
   `DF`, `DP` travel in the access token's `roles` claim; a principal's CM identity is their Keycloak subject.
2. **Browser apps share one public client, `cm-shell`**, with the authorization code flow and PKCE (S256). Each
   micro-frontend runs its own session ([ADR-009](adr-009-independent-micro-frontends.md)).
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
- `directAccessGrantsEnabled` remains on `cm-shell` for the scripted verification only, with the seeded demo users'
  passwords; it is to be turned off once the scripts sign in with codes.
- The realm file is applied only on first import; changing it needs a fresh import locally and an admin-API migration
  in a long-lived environment.
