# ADR-023: Breach response and privacy governance in their own context

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.7](../compliance/compliance-architecture.md)

## Context

Each regime sets its own breach-notification recipients and clocks:

- **GDPR:** the supervisory authority within 72 hours.
- **DPDPA:** the Board without delay, with a detailed report within 72 hours **[confirm]**; CERT-In within 6 hours.
- **HIPAA:** individuals, HHS and sometimes the media, within 60 days; a business associate reports to the covered
  entity as its BAA says.
- **Saudi Arabia, UAE, DIFC, ADGM, Qatar, Bahrain, Oman:** their own rules.

As a processor, the platform must also help tenants meet their obligations. Accountability needs:

- records of processing (GDPR Art. 30)
- a sub-processor register (Art. 28)
- transfer records (Saudi risk assessments)
- DPIAs

None exist today.

## Decision

1. **A new service, `cm-gov-svc` (Privacy Governance), per cell, owns:**
   - the `Incident` aggregate, with one notification clock per regime and recipient, computed from the regime profile
     at classification (ADR-020)
   - records of processing (generated from purpose definitions and the platform's own processing)
   - the sub-processor register
   - transfer impact records
   - DPIA records
2. **Incidents can be opened by:** staff, a tenant (through the DF app and API), or an automated detector (anomalous
   access in the audit ledger).
3. **Clocks drive alerts and notification drafts.** Sending is a human act, with evidence recorded, written to the
   ledger and timestamped (ADR-015).
4. **Incident records hold counts and pseudonyms, never contact data.** Notifying individuals goes through the vault
   and notifications.

## Consequences

- Deadlines become visible and evidenced, and processor duties to tenants are explicit.
- One more service per cell, mostly CRUD with clocks; it can start inside `cm-stf-svc` if needed, but its data is
  per cell either way.

## Compliance impact

GDPR Arts. 28, 30, 33-35, DPDPA s.8(6) and Rule 7, HIPAA §§164.400-414, and the Gulf laws' breach duties.
