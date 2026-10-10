# cm-realm.json notes

(Not embeddable in the JSON itself — Keycloak's realm import uses strict Jackson
deserialization and rejects unrecognized fields.)

- **`cm-df-api` client-credentials clients are not statically defined here.**
  `cm-df-reg-svc`'s `KeycloakDfApiClientProvisionAdapter` (plan §10) creates one dynamically
  per DF (`df-api-<dfId>`) at activation time via the Admin REST API, each with a
  hardcoded-claim `df_id` mapper.
- **Browser sign-in is passwordless (plan §9a).** The realm's `browserFlow` is `cm-otp-browser`:
  an existing SSO session, otherwise `cm-idn-otp-spi`'s one-time code by email or SMS, sent
  through cm-ntf-svc. Being the realm-wide browser flow, it applies to every app that signs in
  through cm-shell (CM Admin, DF app, DP portal) and to the account console. The authenticator
  config `cm-otp` sets the cm-ntf-svc URL, code validity (300 s, matching cm-ntf-svc's OTP
  validity), wrong guesses per code (5) and the role given to accounts created by signing in
  (`DP`; empty allows existing accounts only).
- **Signing in creates a DP account.** An address with no account that proves itself with a code
  becomes a DP user (username = the email or phone number, phone in the `phone` attribute), so
  `registrationAllowed` is `false` and the password registration page is gone.
- **The user profile asks for nothing but a username.** Keycloak 25's default profile requires
  first name, last name and email, which would stop a new DP at an "update profile" page and ask
  a phone-only DP for an email. CM doesn't need a DP's name, so the `declarative-user-profile`
  component in `components` keeps those attributes but makes them optional.
- **Brute-force protection is on** (`bruteForceProtected`, 10 failures, waits from 1 to 15
  minutes): wrong codes count against the account across sign-ins, on top of the per-code limit.
- **Applying a changed realm file** needs a fresh import: `--import-realm` skips a realm that
  already exists. Locally, recreate the dev container
  (`docker compose up -d --force-recreate keycloak`; its dev database lives in the container).
- **`directAccessGrantsEnabled: true` on `cm-shell` is a TESTING-ONLY convenience** so
  curl-based verification (`scripts/verify-phase1.sh`) can still obtain tokens with the seeded
  demo users' passwords. Browser sign-in no longer uses passwords; turn this off once the
  verification scripts sign in with codes (`scripts/verify-otp-login.mjs` shows how).
- **`scripts/verify-otp-login.mjs`** checks the browser sign-in end to end: invalid address,
  wrong code, a seeded admin keeping `CM_ADMIN`, and a new address becoming a DP (codes read
  from Mailpit).
