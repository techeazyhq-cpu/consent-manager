# ADR-025: Consent records and receipts to ISO/IEC TS 27560, in DPV

- **Status:** Accepted, implemented by [ADR-046](adr-046-consent-records-receipts-and-export.md); receipt inclusion proofs, PDF/UA receipts and the WORM archive proposed
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

1. **Every consent has an ISO/IEC TS 27560 record, owned by the Consent write side** (`cm-cns-cmd-svc`, finding R5).
   - **Content:** Header, Notice (version id, hash, language), Processing (purposes and data categories mapped to DPV,
     sensitivity, recipients including transferees, controller, legal basis `consent`, the applicable jurisdictions
     (ADR-020), retention, transfers, withdrawal method, rights), Parties and Events.
   - **How it's kept:** an append-only document, extended in the same transaction as each state change, so it can never
     disagree with the consent's state.
   - **The read side** (`cm-cns-qry-svc`) serves copies only, and can be rebuilt from the write side.
   - **States:** requested, given, refused, renewed, modified, withdrawn, lapsed, terminated by the controller,
     invalidated by an authority (ADR-029).
   - **Form:** JSON-LD with DPV, validated against a JSON schema in CI.
2. **A receipt is issued** to the principal on every state change, and offered to the fiduciary, downloadable with all
   of the principal's records (export, B4). It is signed with a **dedicated receipt key**, separate from the token key
   (finding R8):
   - Receipt keys rotate on their own crypto-period. Retired public keys stay published for as long as any receipt
     they signed is retained, so verification works for 7 years and more.
   - Each receipt gets **trusted time**. Its hash goes into the next audit-ledger checkpoint, which an RFC 3161
     authority timestamps (ADR-015), and the receipt can be fetched with its inclusion proof.
   - Algorithm migration: when an algorithm weakens, retained receipts are re-timestamped under a stronger one before
     the old one is retired.
3. **Every version of every record is hashed into the audit ledger's chain** (ADR-014, ADR-015). That makes records
   tamper-evident, with erasable identifying payloads (ADR-019). Records are kept for the regime profile's retention
   (ADR-020). After their active period they move to an **in-cell, object-locked (WORM) archive**, still covered by
   the chain.
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
