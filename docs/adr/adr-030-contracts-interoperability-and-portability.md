# ADR-030: Contract-first interfaces, interoperability, and moving to another consent manager

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.2, Appendix B findings R14, R15](../compliance/compliance-architecture.md)

## Context

Fiduciaries build their systems against the platform's APIs, webhooks and records. DPDPA requires an **interoperable**
platform (s.2(g)), and registration needs certification against the Board's interoperability standard (First Schedule
Part A item 9), which is not yet published.

A principal may also want to move from one registered consent manager to another. Today the platform exports records
(ADR-025) but has no way to import them, or to hand consents over.

## Decision

1. **Contracts first:**
   - **Formats:** OpenAPI 3.1 for every API fiduciaries and principals use; AsyncAPI 3 for webhooks and published
     events; a pinned JSON-LD context version for 27560 records and receipts.
   - **Location and enforcement:** the contracts live in the repository, are published per cell, and are the source of
     generated clients. Consumer-driven contract tests run in CI.
2. **Signed webhooks:**
   - Every webhook carries an id, a timestamp and a signature over both, with the receipt key family (ADR-025), and is
     verifiable against the published JWKS.
   - Delivery is at least once from the outbox, with retries and a dead-letter queue.
   - Each delivery attempt is recorded.
3. **Versioning:** additive changes only within a major version. A major version is supported for at least 12 months
   after its successor ships (estimate, to agree with fiduciaries). Deprecation is announced in the contracts and by
   notice to fiduciaries.
4. **Interoperability as mappings:** the domain model is neutral. ISO/IEC TS 27560, FHIR R4 `Consent` (ADR-024) and
   India's DEPA consent artefact are mappings at the boundary. The Board's standard becomes one more mapping when
   published.
5. **Moving to another consent manager:**
   - **Export:** the principal downloads, or authorises a transfer of, their signed receipts.
   - **Import:** the platform accepts receipts issued by another consent manager. It verifies their signatures, and
     with the fiduciaries' confirmation re-records the consents here, linking the originals.
   - **Closing out:** the principal's account at the previous consent manager can then be closed through that
     manager's own process.

   A consent is never active at two consent managers for the same fiduciary and purpose: import requires the
   fiduciary to confirm it has switched.

## Consequences

- Fiduciaries get stable, versioned contracts, and interoperability can be certified against published contracts.
- Portability depends on other consent managers issuing standard receipts. Until the Board's standard exists, import
  supports 27560 receipts only.
- More artefacts to keep current: contracts, generated clients and contract tests.

## Compliance impact

DPDPA s.2(g) (interoperable platform) and First Schedule Part A item 9; ISO/IEC TS 27560 (records and receipts as the
exchange format).
