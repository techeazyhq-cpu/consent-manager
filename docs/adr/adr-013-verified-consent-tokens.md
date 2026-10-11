# ADR-013: Consent tokens are verified, and bound to their artefact and fiduciary

- **Status:** Accepted; supersedes the token handling in [ADR-006](adr-006-consent-token-and-pep-pdp.md). Offline verification by fiduciaries is superseded by [ADR-027](adr-027-short-lived-tokens-status-list-and-verification-availability.md) (proposed): short-lived tokens and a revocation status list
- **Date:** 2026-10-10

## Context

ADR-006 recorded a security gap: no one verified a consent token's signature. The signing key was generated in memory
at every start of `cm-cns-cmd-svc` and published nowhere, so OPA could only decode tokens. Whoever knew the id of an
issued artefact could present a forged token with any purpose, for any fiduciary, unsigned (`alg: none`) or expired,
and be allowed while the artefact was live; and a restart made every earlier token unverifiable. New policy tests run
against the old policy confirmed it: it allowed 6 of 9 cases that must be denied.

## Decision

1. **Persistent signing keys.** `CnsTknKeys` keeps the RSA keys in a JSON Web Key Set file (`cm.cns-tkn.keys-path`;
   in Compose the `cns-tkn-keys` volume at `/var/lib/cm/keys`). On first start it generates one (2048-bit, `RS256`,
   `kid` `cns-tkn-<timestamp>`) when `cm.cns-tkn.create-keys-if-missing` allows it, written readable by the owner
   only; production mounts a managed key set and turns creation off, so a missing key stops the service instead of
   silently creating a new one.
2. **Rotation without breaking tokens.** The first key in the set signs; every key in it verifies. Rotate by putting a
   new key first, at least five minutes after publishing it (OPA's key cache), and removing the old key once the last
   token it signed has expired.
3. **Published public keys.** `cm-cns-cmd-svc` serves the public halves at
   `GET /api/cns-cmd/.well-known/jwks.json` (`application/jwk-set+json`, cached 5 minutes), unauthenticated in the
   service and at the gateway, for OPA and for fiduciaries who verify tokens themselves.
4. **Tokens name what they are for.** Every token carries `iss` = `cm-cns-cmd-svc`, `iat`, `cns_art_id` (the artefact
   it was issued for) and `kid` in its header, besides the purpose, fiduciary, access type, scope and expiry.
5. **The policy verifies everything.** `cns_authz.rego` allows only when `io.jwt.decode_verify` accepts the token
   against the published keys (signature, `RS256`, issuer, expiry) **and** the token's `cns_art_id` is the artefact
   asked about, its `fiduciary_id` is the caller's own `df_id` (passed by the gateway from the caller's access token),
   it grants the requested purpose, and the artefact is still `ISSUED`. No keys, no caller identity or no live status
   means deny.
6. **The policy is tested.** `cns_authz_test.rego` covers each rule with test-only keys, including a token forged with
   another key under the same `kid`; `opa test infra/opa/policies` runs in CI. OPA is pinned (1.20.1) instead of
   `latest-envoy`.

## Consequences

- A token proves who issued it, for which artefact and to whom; possession of an artefact id is worth nothing.
- Tokens issued before this change carry no `cns_art_id` and no `iss`, and are denied; they were unverifiable anyway.
  A fiduciary gets a valid token with the next grant.
- Callers without a fiduciary identity (administrators, principals) cannot use the verification endpoint, which was
  never meant for them.
- The signing key's protection is now an operational duty: the key file or its managed source must be backed up and
  access-controlled like any credential. Moving it into a KMS or HSM is the next hardening step.
