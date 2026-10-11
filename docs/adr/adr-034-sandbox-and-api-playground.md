# ADR-034: A sandbox with synthetic principals, and an API playground in the developer portal

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [product architecture §4](../product/product-architecture.md)

## Context

Fiduciaries can only test their integration against production today. That means real principals, real
notifications, real consent records that must be kept for 7 years, and billing. Integrations also need to prove they
handle the hard cases before going live: verifying before each use, refreshing short-lived tokens, honouring a
withdrawal, reading the status list (ADR-027).

## Decision

1. **A sandbox environment:** the same images and contracts as production, deployed separately.
   - **Synthetic principals only.** Contact points must match reserved test patterns (for example
     `*@sandbox.example`, a reserved number range), and anything else is refused.
   - **No delivery.** Notifications go to an in-sandbox inbox and are never sent.
   - **Its own signing keys and issuer,** so a sandbox token never verifies in production, and the reverse.
   - **No billing; data reset periodically.**

   Because it holds no personal data, one sandbox serves every region.
2. **The developer portal (`mf-dev-portal`)** offers:
   - **Docs** generated from the OpenAPI and AsyncAPI contracts (ADR-030), with guides per journey and a changelog.
   - **A playground:** try any call against the sandbox with the fiduciary's sandbox credentials, see the request and
     response, and copy it as `curl` or SDK code.
   - **A principal simulator:** act as a synthetic principal (give, refuse, partly give, shorten, withdraw, let lapse)
     and watch tokens, status lists and webhooks change.
   - **A webhook inspector:** a hosted endpoint showing each delivery, its signature check, and replay.
3. **Conformance checks before production.** A scripted suite runs a fiduciary's integration through the required
   behaviours in the sandbox:
   - verify before use
   - refresh tokens
   - honour a withdrawal within the token lifetime
   - read the status list
   - verify webhook signatures

   Production API clients are issued only after the suite passes. The result is kept as evidence for the platform's
   certification (First Schedule Part A item 9).
4. **Sandbox accounts:** developers sign up with a work email, and are attached to their fiduciary once it is
   verified (ADR-029). Sandbox use never touches the fiduciary's production data.

## Options considered

- **A test mode inside production** (test keys, flagged rows): less to run, but synthetic and real data share stores,
  keys, retention and backups, and one mistake writes a real person.
- **Mocks only (no sandbox):** cheap, but they drift from the real behaviour, and can't prove withdrawal handling end
  to end.

## Consequences

- Fiduciaries integrate and prove correct handling without any risk to real principals.
- One more environment to run (small, one for every region).
- Conformance checks gate onboarding, which lengthens time to go live a little, by design.

## Compliance impact

- Data minimisation: no personal data in testing.
- DPDPA s.6(4) and First Schedule B7, enforced in fiduciaries' integrations.
- Evidence for Part A item 9 certification.
