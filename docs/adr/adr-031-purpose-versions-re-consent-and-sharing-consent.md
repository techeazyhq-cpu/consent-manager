# ADR-031: Purpose definitions are versioned; a material change needs re-consent; sharing consent binds both parties

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.2, Appendix B findings R4, R10](../compliance/compliance-architecture.md)

## Context

Consent is given for a specified purpose, with the data, recipients, retention and transfers described in the notice:
DPDPA s.6(1), GDPR Arts. 5(1)(b), 6(4) and 7, and ISO/IEC 29184, which asks for notice of changes. Today a purpose is
a free-text code on the request, so nothing ties a consent to what the principal was actually told, or says what
happens when the fiduciary changes it.

DPDPA's First Schedule also lets a principal consent to a fiduciary sharing data with another onboarded fiduciary
(B1), and requires a record of that sharing (B3). Both parties then hold the data, and both must stop when consent is
withdrawn (DPDPA s.6(6); GDPR Arts. 17(2), 19).

## Decision

1. **A purpose definition is an aggregate in the Consent context, with immutable versions.** Each version holds:
   - description
   - data categories (DPV-mapped, with sensitivity and the `necessary` flag of ADR-026)
   - recipients and transfers
   - retention
   - requested and maximum validity (ADR-026)
   - flags (ADR-022)
   - localised notice versions

   A consent request names exact purpose versions. A consent stays bound to the version it was given for, and its
   27560 record says which.
2. **A change creates a new version, classified by the platform, not by the fiduciary:**
   - **Material** (needs re-consent): a new purpose, more data categories, new recipients or transfers, longer
     retention or maximum validity, a sensitivity raised.

     Existing consents keep covering only what their version covered. The fiduciary raises re-consent requests for
     the new version. Principals who don't consent keep the old scope until it lapses or is withdrawn.
   - **Non-material** (wording, contact details, fewer categories, shorter retention): principals are told, and the
     record links the new version. No new consent is needed.

   The classification compares versions field by field, so it is deterministic and testable.
3. **Sharing consent:**
   - A principal can consent to fiduciary A sharing named categories, for a named purpose version of fiduciary B, with
     onboarded fiduciary B. B's purpose and notice are shown to the principal, not only A's.
   - The 27560 record lists A as the controller sharing, and B as a recipient and controller. Each verifies its own
     token: A's to share, B's to use.
   - A withdrawal, shortening, lapse or termination reaches **both** A and B: change webhooks, status and the status
     list (ADR-027).
   - The data moves from A to B directly, never through the platform (ADR-023).
4. **Sharing stays within a cell.** Sharing across cells is refused, unless the purpose version records a lawful
   transfer mechanism allowed by every applicable regime (ADR-018, ADR-020). Even then, only the consent is recorded in
   both cells; no personal data crosses.

## Options considered

- **Let fiduciaries edit purposes in place:** existing consents would silently cover something the principal never saw.
- **Every change needs re-consent:** fatigue, and pressure to "accept" without reading. Fewer categories or shorter
  retention only reduce what the consent allows.

## Consequences

- Every consent can be traced to the exact text, data and recipients the principal agreed to, and a fiduciary can't
  widen a consent without asking again.
- Fiduciaries manage purpose versions and run re-consent campaigns, which the platform rate-limits (ADR-028).
- Sharing consent adds a second fiduciary to the change-propagation path of every shared consent.

## Compliance impact

DPDPA s.6(1), (6) and First Schedule B1, B3; GDPR Arts. 5(1)(b), 6(4), 7, 17(2), 19; ISO/IEC 29184 (notice of
changes); ISO/IEC TS 27560 (Processing and Parties sections).
