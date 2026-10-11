# ADR-029: Fiduciaries are verified at onboarding; termination by a fiduciary or an authority

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.6, Appendix B findings R11, R16](../compliance/compliance-architecture.md)

## Context

Principals rely on the consent manager for who is asking them. An unverified requester is a phishing route against the
people the platform acts for (DPDPA First Schedule B8, Part A item 8). Onboarding today records a fiduciary's
registration numbers (CIN, GSTIN) without checking them.

ISO/IEC TS 27560 has consent states for termination by the controller and invalidation by an authority. ADR-025 lists
them, but nothing defines what happens when a fiduciary leaves the platform, is suspended, or is ordered by a
regulator (for example by the Board's directions under DPDPA ss.27-28) to stop.

## Decision

1. **Verification at onboarding,** before activation. The fiduciary's legal identity is checked against the registry
   of its cell:

   | Cell | Registries |
   |---|---|
   | IN | MCA (CIN), GST (GSTIN) |
   | AE | Trade licence, DIFC and ADGM registers |
   | SA | Commercial registration |
   | EU | National company registers, VAT (VIES) |
   | US | EIN, and NPI for covered entities |

   The registry, reference and time of the check are recorded. A fiduciary that can't be verified stays `PENDING`.
2. **Requests show the fiduciary's verified legal name** and a "verified" mark. Display names can't impersonate
   another verified fiduciary.
3. **Suspension** (fraud, unpaid terms, a regulator's direction) stops new requests and refuses token issuance at
   once. Existing consents stay recorded, and fiduciaries and principals are told.
4. **Termination by the controller:** a fiduciary leaving the platform, or ending a purpose, terminates its consents.
   For each one:
   - a 27560 `terminated by controller` event and the reason
   - a receipt to the principal
   - a webhook to the fiduciary
   - the consent stops verifying
5. **Invalidation by an authority:** a regulator's order is entered by staff with the order reference, through a
   two-person approval, and recorded as `invalidated by authority` with the reference. Principals and fiduciaries are
   told.

## Consequences

- Principals can trust who is asking, and every way a consent ends is recorded the same way.
- Onboarding depends on registry checks per cell, each through an adapter behind a circuit breaker (ADR-016). Where no
  registry API exists, a documented manual check with evidence stands in.

## Compliance impact

DPDPA First Schedule B8 and Part A item 8; DPDPA ss.27-28; ISO/IEC TS 27560 §8.5 states.
