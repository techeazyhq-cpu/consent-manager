# cm-realm.json notes

(Not embeddable in the JSON itself — Keycloak's realm import uses strict Jackson
deserialization and rejects unrecognized fields.)

- **`cm-df-api` client-credentials clients are not statically defined here.**
  `cm-df-reg-svc`'s `KeycloakDfApiClientProvisionAdapter` (plan §10) creates one dynamically
  per DF (`df-api-<dfId>`) at activation time via the Admin REST API, each with a
  hardcoded-claim `df_id` mapper.
- **The passwordless Email/SMS OTP browser flow (plan §9a) is not wired in yet.**
  Once `cm-idn-otp-spi` exists and is deployed into the Keycloak container, `cm-shell`'s
  `authenticationFlowBindingOverrides.browser` should point at that custom flow, replacing
  the realm's default password-based browser flow.
- **`directAccessGrantsEnabled: true` on `cm-shell` is a TESTING-ONLY convenience** so
  curl-based verification (`scripts/verify-phase1.sh`) can obtain tokens for the seeded demo
  users before the OTP SPI and frontends exist. Flip back to `false` once the OTP flow is
  wired in — the real design is passwordless (plan §9a).
