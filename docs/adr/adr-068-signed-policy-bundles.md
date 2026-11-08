# ADR-068: The authorisation policy ships as a signed, versioned bundle

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #97

## Context

Every consent check waits on one policy (`infra/opa/policies/cns_authz.rego`, ADR-009): the policy decides whether a fiduciary may
use a consent. It reached the running platform by being **copied into the Helm chart** (`files/opa`, kept equal to the source by a CI
`diff`) and mounted from a ConfigMap, so changing it meant a chart release, and nothing proved that what OPA ran was what CI had
tested. A policy that decides access is the most valuable thing to tamper with: loading an altered one is loading "allow everything".

## Decision

1. **A bundle.** [`scripts/opa-bundle.sh`](../../scripts/opa-bundle.sh) checks the Rego strictly, runs its tests, and builds an OPA
   bundle (the policy without its tests) named `cm-authz-<version>.tar.gz`, with the Git revision it was built from in the bundle's
   manifest and a `manifest-<version>.json` beside it holding the version, the revision, the SHA-256 and whether it is signed. The
   version is `<date>-<short revision>` (and `-dirty` when the policy differs from what is committed, so such a bundle is never
   mistaken for the committed one). The same script runs on a laptop and in CI.
2. **Signed.** Given an RSA private key, the build signs the bundle (RS256, OPA's own bundle signing) and then **verifies the
   signature with the matching public key before the bundle may leave**. The private key lives only in CI's secrets
   (`OPA_BUNDLE_SIGNING_KEY`) and is never printed or put on a command line.
3. **Loaded only if it verifies.** With `opa.bundle.enabled`, the chart runs OPA with a configuration that pulls the bundle from
   `opa.bundle.serviceUrl`, polling, and **verifies its signature against the public key in the values**. A bundle altered after it
   was signed, or signed by another key, is not loaded: OPA has no policy and never becomes ready (the readiness probe asks
   `/health?bundles`), so the gateway answers 503 and never decides on a policy nobody signed (ADR-057). The chart refuses to render
   a bundle without a public key, or without a service URL.
4. **The bundle owns one root.** `infra/opa/policies/.manifest` gives the bundle the `consent` root. The release-specific data
   (where cm-cns-cmd-svc is) stays in the chart, under `config`, so one bundle serves every release and the sandbox.
5. **Default unchanged.** With `opa.bundle.enabled` false (the default) the chart mounts its own copy of the policy as before, and
   the local docker compose stack runs the folder. The `diff` between the source and the chart's copy stays in CI.
6. **CI** (`opa-bundle` job): runs [`tests/security/e2e-opa-bundle.mjs`](../../tests/security/e2e-opa-bundle.mjs), which signs a
   bundle with a throwaway key, serves it, and starts real OPA servers against it: the right key loads it and evaluates the
   policy; a copy whose policy was changed to `allow := true` after signing is refused; a different key refuses it. Then it builds
   the bundle (signed on `main` when the signing key is set, unsigned otherwise, saying so) and uploads it and its manifest as a
   build artifact. A separate step renders the chart in bundle mode and checks it against Kubernetes' schemas.

## Not yet

- **Publishing.** The pipeline builds, signs and uploads the bundle as a build artifact; it does not put it where OPA pulls from
  (an object-storage bucket or registry behind HTTPS, in each cloud). That is a decision about each cloud's storage and who
  may write to it, and it should be a step of its own: copy the signed bundle to `serviceUrl`, then repoint the stable name
  `cm-authz.tar.gz` at it. See the runbook.
- **Bundle-service authentication.** OPA can send credentials to the bundle service; the chart does not configure any, so the
  service has to be reachable without them (a private address) or the chart extended. The signature, not the transport, is what
  makes the bundle trustworthy.
- **Key rotation without a gap** is a manual procedure (two keys in the OPA configuration, then drop the old one); the chart takes one key.
- **Decision logging and policy-change approval** (who approved this policy change) beyond the pull request that changed the Rego.
- **A running OPA that is offered a bad bundle** keeps the last good policy, which is OPA's documented behaviour; the end-to-end
  test checks the start-up case (no policy at all), not that one.

## Options considered

- **Keep copying the policy into the chart:** simple, ties every policy change to a chart release, and gives no proof that the
  running policy is the tested one.
- **OPA's own bundle service (Styra DAS) or a vendor:** more than needed; the signed file and a static host do it.
- **An OCI registry for the bundle:** OPA supports it and it signs and versions well; it adds a registry write path per cloud, which
  is the same unmade decision as publishing, and can replace the static host later without changing the bundle.
- **Signing with a shared secret (HMAC):** every OPA would hold the key that can sign, so one compromised pod could ship a policy.
  Asymmetric signing keeps the signing key off the cluster.
