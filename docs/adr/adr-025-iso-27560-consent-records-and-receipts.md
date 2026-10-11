# ADR-025: Consent records and receipts to ISO/IEC TS 27560, in DPV

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §2, §6.1](../compliance/compliance-architecture.md)

## Context

Every regime makes the consent record the evidence:

- **GDPR Art. 7(1):** the controller must demonstrate consent.
- **DPDPA:** the consent manager must keep records of consents given, denied and withdrawn, of notices and of sharing,
  tamper-proof, and give principals machine-readable copies for at least 7 years (First Schedule B3, B4).
- **HIPAA:** authorisations must be documented and retained.

ISO/IEC TS 27560:2023 standardises the record and a receipt, a copy issued to another party:

- **Sections:** Header, Notice, Processing, Parties and Events.
- **Mandatory fields** include schema version, record id, subject, notice and its language, purposes, personal data
  and its type, controller, recipients, jurisdiction, how to withdraw, rights, and event time, type and state.
- **Form:** the W3C DPV community group publishes a JSON-LD profile using DPV concepts.

Today's artefact holds purpose code, categories, status and dates only. It has no notice version, language, controller,
recipients, jurisdiction, rights, withdrawal method or event states, and no receipt is issued.

## Decision

1. **Every consent has an ISO/IEC TS 27560 record**, built by `cm-cns-qry-svc` from the consent events and the purpose
   definition: Header, Notice (version id, hash, language), Processing (purposes and data categories mapped to DPV,
   sensitivity, recipients, controller, legal basis `consent`, jurisdiction, retention, transfers, withdrawal method,
   rights), Parties and Events.
   - **States:** requested, given, refused, renewed, modified, withdrawn, lapsed, terminated by the controller.
   - **Form:** JSON-LD with DPV, validated against a JSON schema in CI.
2. **A receipt is issued** to the principal on every state change, and offered to the fiduciary. It is signed with the
   platform's key (the JWKS of ADR-013) and downloadable with all of the principal's records (export, B4).
3. **Records are anchored in the audit ledger's hash chain** (ADR-014, ADR-015): tamper-proof, with erasable
   identifying payloads (ADR-019), and kept for the regime profile's retention (ADR-020).
4. **The domain model stays neutral.** 27560, FHIR R4 `Consent` (ADR-024) and India's DEPA consent artefact are
   mappings at the boundary. When the Board publishes its interoperability standard (Part A item 9), it becomes one
   more mapping.

## Options considered

- **Kantara Consent Receipt v1.1:** superseded by 27560.
- **A proprietary record format:** fails the interoperability the Board will certify and that controllers need to
  prove consent.

## Consequences

- Consent is provable in one standard form across every regime, and principals hold their own signed copies.
- Every purpose definition must map its categories to DPV, which is extra onboarding work for fiduciaries, supported
  by a picker in the DF app.
- The ISO text is paid; the CI schema is built from the DPV profile and checked against the purchased standard.

## Compliance impact

GDPR Art. 7(1); DPDPA First Schedule B3 and B4 and Part A item 9; HIPAA §164.508 documentation; MeitY's
consent-management BRD (lifecycle logging, downloadable history).
