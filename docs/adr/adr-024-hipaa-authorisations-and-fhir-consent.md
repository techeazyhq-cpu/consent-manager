# ADR-024: HIPAA authorisations in the consent model; FHIR Consent at the boundary

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.4, §6.8](../compliance/compliance-architecture.md)

## Context

US covered entities will use the platform to record patients' authorisations, making it a business associate.

- **§164.508(c) requires** core elements (the information, who discloses it, to whom, the purpose, an expiry date or
  event, the signature and date) and statements (the right to revoke, conditioning, redisclosure).
- **Health ecosystems exchange consent as HL7 FHIR:** US Core, India's ABDM consent artefacts, NPHIES in Saudi Arabia,
  Malaffi and NABIDH in the UAE.
- **The current consent model** has plain data-category codes and no element checks.
- **The Security Rule** currently treats encryption as addressable and doesn't name MFA. The January 2025 proposal
  would require both; final action is targeted for 2027 **[confirm]**.

## Decision

1. **Data categories get a sensitivity** (`ORDINARY`, `SENSITIVE`, `PHI`, `CHILD`) in purpose definitions. A purpose
   with PHI under the HIPAA regime makes its consent requests HIPAA authorisations. The regime profile lists the
   required elements, and the request is refused without them.
2. **The artefact records** the elements, the expiry event, the signer (principal or personal representative, ADR-022)
   and a step-up signing ceremony: passkey or a verified second channel.
3. **A new service, `cm-fhir-svc`, is an anti-corruption layer.**
   - It exposes artefacts as FHIR R4 `Consent` resources: read and search, and accepting FHIR `Consent` for
     authorisations captured elsewhere.
   - It records reads as `AuditEvent` and lineage as `Provenance`, and is authorised with SMART on FHIR scopes.
   - R4 is chosen because it is what the target ecosystems deploy.
   - The domain model never holds FHIR types.
   - It exchanges consent only. It is not a clinical data repository, and never receives the health data an
     authorisation governs (ADR-023).
   - Resources are validated in CI with the FHIR validator against the chosen implementation guides **[confirm IGs per
     cell]**.
4. **The US cell runs the HIPAA safeguards whether or not the proposal becomes final:** encryption at rest and in
   transit, mTLS, staff MFA, a record of every access to principal-level data, and 6-year retention of authorisation
   documentation.
5. **Events never carry PHI or FHIR payloads.** They carry ids; consumers fetch through authorised APIs.
6. **An electronic-signature ceremony for authorisations** (finding R13), designed to the US ESIGN Act and state UETA
   **[confirm with counsel]**:
   1. disclosure, and the patient's consent to sign and receive records electronically, with how to get a paper copy
   2. the full authorisation text and version, shown before signing
   3. a distinct act of intent ("Sign authorisation"), separate from any other consent
   4. the signature bound to the hash of the exact text, version and signer (patient or personal representative), with
      the step-up authentication level
   5. a copy to the patient at once (§164.508(c)(4)), as the receipt (ADR-025)

   Compound authorisations are refused, except where §164.508(b)(3) allows them: an authorisation is never combined
   with another consent or document.

## Consequences

- One consent model serves DPDPA, GDPR and HIPAA, and FHIR keeps the platform interoperable with health ecosystems.
- One more service, plus FHIR conformance work per ecosystem.
- BAAs with every sub-processor in the US cell.

## Compliance impact

HIPAA §§164.508, 164.502(g), 164.312 and 164.530(j). The design is aligned with the proposed Security Rule. It also
covers the interoperability needs of ABDM in India and the Gulf health information exchanges.
