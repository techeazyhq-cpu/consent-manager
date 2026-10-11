# ADR-028: An accessible consent experience, without deceptive design, with recorded evidence

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.2, Appendix B findings R6, R7, R17](../compliance/compliance-architecture.md)

## Context

DPDPA s.2(g) defines a consent manager as an **accessible** platform, and its principals include persons with
disabilities. Consent obtained through deceptive design isn't freely given:

- GDPR Art. 7(4) and the EDPB Guidelines 03/2022 on deceptive design patterns
- India's Guidelines for Prevention and Regulation of Dark Patterns, 2023 (CCPA)
- ISO/IEC 29184

ISO/IEC TS 27560 requires a record of who expressed consent and recommends recording how. The device and IP address
are personal data, though, and should be captured only where needed.

## Decision

1. **Accessibility:**
   - **Target:** WCAG 2.2 level AA for the DP portal, notices, the decision screen and receipts, plus EN 301 549 in
     the EU cell, Section 508 in the US cell and GIGW in the IN cell.
   - **Receipts:** issued as tagged PDF/UA as well as JSON-LD.
   - **Testing:** automated accessibility checks (axe) in CI for every consent screen; manual screen-reader audits
     each release; an assisted channel for principals who need help.
   - **Language:** notices and receipts in the principal's chosen language, right-to-left for Arabic.
2. **The decision is always rendered by the platform's own components.** Fiduciaries supply text and a logo, never
   the controls, colours or order of choices.
3. **Rules the components enforce:**
   - Accept and refuse have equal prominence and take equal effort.
   - Optional purposes and categories start unselected.
   - Withdrawing takes no more steps than giving.
   - No confirm-shaming, false urgency or countdowns.
   - A refused request can't be raised again with the same principal for a period set by the regime profile, and
     renewal reminders are capped by count and spacing (ADR-026).
   - The default validity is the requested one, never the maximum (ADR-026).

   UI tests assert each rule, so a change that breaks one fails the build.
4. **Evidence of expression, minimised.** Each consent event records:
   - who expressed it: the principal, or a representative and their authority
   - the method: the component and its version, and the channel
   - the authentication assurance level of the session

   IP address or device data is captured only where a regime profile requires it, encrypted in the vault (ADR-019).

## Consequences

- Consent obtained through the platform can be shown to be freely given and accessible, from the record itself.
- Fiduciaries can't brand the decision beyond text and logo; some will want more.
- Every consent screen change runs accessibility and deceptive-design tests.

## Compliance impact

DPDPA s.2(g) (accessible platform); GDPR Art. 7(4); EDPB Guidelines 03/2022; India's dark-pattern guidelines (2023);
ISO/IEC 29184; WCAG 2.2; ISO/IEC TS 27560 §8.5 (expression by entity and method).
