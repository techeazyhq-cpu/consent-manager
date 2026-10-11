# ADR-021: A Data Principal Rights context, with an orchestrated erasure saga

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.5, §6.6](../compliance/compliance-architecture.md)

## Context

Every regime in scope grants rights with deadlines, though the set varies by regime: access, correction, erasure,
portability, nomination (DPDPA), objection and restriction (GDPR), grievance (DPDPA), and access to and amendment of
PHI (HIPAA). Some requests concern the platform's own records (consents, notices shown); others concern data only the
fiduciary holds. Erasure must reach every context and Keycloak, yet keep what a legal hold requires: DPDPA consent
records for 7 years **[confirm]** and HIPAA authorisation documentation for 6 years.

## Decision

1. **A new service, `cm-dsr-svc`, owns the `RightsRequest` aggregate.**

   **Invariants:**
   - The deadline is fixed at receipt from the regime profile (ADR-020).
   - Nothing is fulfilled before the requester's identity or authority is verified.
   - Refusals cite a legal ground.
   - Extensions are only those the regime allows, with notice to the requester.
2. **Requests about a fiduciary's own data are routed to it as tasks** (DF app and API webhook). The service tracks
   their answers against the deadline, and alerts at 50% and 80% of the window.
3. **Erasure is an orchestrated saga** (one place sees its progress and owns the deadline), run in this order:
   1. notice
   2. revoke active consents
   3. ask fiduciaries to erase
   4. delete push devices
   5. erase audit payloads and subject links
   6. delete the Keycloak user
   7. shred the subject key (ADR-019)

   Steps are idempotent and retried. Shredding is the point of no return, so every reversible step comes first; the
   saga recovers forward, never compensates after it. A step that can't complete moves the request to `STUCK` with an
   alert and a runbook.
4. **Legal holds** keep the pseudonymised consent record and remove only contact data. The requester is told what is
   held, why, and until when.
5. **Access and portability exports** (JSON and PDF, plus FHIR `Consent` for health) are produced in the principal's
   cell, and offered for download only after re-authentication.

## Options considered

- **Choreography (each context reacts to `ErasureRequested`):** no single view of progress or the deadline, and the
  point of no return can't be ordered last.
- **Rights handled inside the Consent context:** mixes the consent lifecycle with a separate lifecycle that has its own
  deadlines and its own parties.

## Consequences

- Rights become measurable against deadlines, with evidence.
- One more service in every cell.
- Fiduciaries must integrate the rights-task webhook. Until a fiduciary does, the DF app is the fallback channel.

## Compliance impact

DPDPA ss.11-14, GDPR Arts. 12-22, HIPAA §§164.524 and 164.526, and the Gulf laws' equivalent rights.
