# Counsel and certification register

- **Owner:** the DPO
- **Date:** 2026-10-11
- **Purpose:** what the [compliance architecture](compliance-architecture.md) can't settle by design. Each item needs a
  decision by counsel, the DPO, a regulator or an auditor. Engineering has prepared what each needs.

How to use it:
- When an item is settled, record the answer, the source and the date here.
- If it changes a figure, change the regime profile entry and set `confirmed: true` (ADR-020).
- Until then, a deployment relying on an unconfirmed figure refuses the affected requests (ADR-020, decision 6). It
  never guesses.

## 1. Legal confirmations

Status for every item: open.

### India (DPDPA)

| # | Question | Where it is used | Current working answer | Source to check |
|---|---|---|---|---|
| L1 | When does Rule 4 (consent-manager registration) commence: 13 or 14 November 2026? Is the Board constituted and accepting applications? | Plan, Phase 0; risks | Mid-November 2026; Board status unknown | Gazette G.S.R. 846(E); MeitY and Board notifications |
| L2 | Does Rule 14 let principals exercise rights against a fiduciary through the consent manager? In what form? | ADR-021 (relay); §1 scope | Relay only, if permitted | DPDP Rules, Rule 14 |
| L3 | Do hosting, message-delivery and timestamping vendors count as "subcontracting or assigning" obligations under First Schedule B6? | §6.6 B6; ADR-023 decision 5 | They are processors under contract, not delegates | First Schedule Part B item 6 |
| L4 | Is the consent-record retention exactly "at least seven years"? What counts as "a longer period required by law"? | ADR-025; regime profile | At least 7 years | First Schedule Part B item 4 |
| L5 | Which age-verification and parental-consent methods are accepted (identity and age already held, virtual token, DigiLocker)? Is any mandatory? | ADR-022 | Any of the three, none mandatory | Rule 10 |
| L6 | Breach to the Board: "without delay", then a detailed report within 72 hours? How does it interact with CERT-In's 6 hours? | Own-breach runbook; regime profile | As stated | Rule 7; CERT-In directions of 28 April 2022 |
| L7 | Is the erasure notice 48 hours, and when does it apply (Rule 8(2), Third Schedule classes)? | ADR-021 saga | 48 hours before erasure | Rule 8, Third Schedule |
| L8 | Grievance redressal deadline (reported as 90 days) and its rule number (numbering disputed) | Own-data grievances | 90 days | Rules 13-14 |
| L9 | Notice languages: English or any Eighth Schedule language, at the principal's choice. Must every language be offered, or only those the fiduciary serves? | ADR-031 notice versions | The principal's choice among those offered | DPDPA s.5(3) |

### US (HIPAA)

| # | Question | Where it is used | Current working answer | Source to check |
|---|---|---|---|---|
| L10 | Does the signing ceremony meet the ESIGN Act and the UETA of each state served? Any state-specific requirements for health authorisations? | ADR-024 decision 6 | Designed to ESIGN and UETA | 15 U.S.C. §7001 et seq.; state UETA and health-privacy statutes |
| L11 | Is the platform a business associate only, or also acting for health plans? What should the BAAs flow down to sub-processors? | ADR-024; US cell | Business associate only | 45 CFR §160.103, §164.504(e) |
| L12 | Status of the Security Rule update (proposed January 2025), and its effect on encryption and MFA timelines | ADR-024 | Proposed; final action targeted 2027 | Federal Register; RIN 0945-AA22 |

### EU and UK

| # | Question | Where it is used | Current working answer | Source to check |
|---|---|---|---|---|
| L13 | Digital-consent age per member state, for the `GDPR_xx` overlays | ADR-020 decision 5 | 16 unless lowered (13-16) | National implementing laws |
| L14 | Does the UK share the EU cell (adequacy route), or need its own? | ADR-018 | Shares, unless counsel decides otherwise | UK adequacy decision and its renewal |

### Gulf

| # | Question | Where it is used | Current working answer | Source to check |
|---|---|---|---|---|
| L15 | UAE PDPL: have the executive regulations been issued? What are the consent, rights, breach and transfer figures? | UAE regime profile | Unknown; all figures unconfirmed | UAE legislation portal; UAE Data Office |
| L16 | UAE health data: must it be stored in the UAE? With what exceptions? | ADR-018 (AE cell) | Kept in country | Federal Law No. 2 of 2019, art. 13; its implementing decisions |
| L17 | DIFC and ADGM figures: capacity, rights deadlines, breach timing | Regime profiles | Unconfirmed | DIFC DP Law 2020; ADGM DPR 2021 |
| L18 | Saudi PDPL: are the 2025 amendments adopted? Is there an in-country storage default? Health-sector localisation? Breach timing (72 hours)? | SA regime profile; ADR-018 | Transfer rules apply; storage default disputed | SDAIA regulations; Implementing Regulations; Data Transfer Regulation |
| L19 | Qatar (PDPPL), Bahrain (PDPL), Oman (PDPL): capacity, rights, breach and transfer figures | Regime profiles (cells on demand) | Unconfirmed | Each law and its regulations |
| L20 | Arabic as the notice language in each Gulf jurisdiction | ADR-031 | Arabic required, English alongside | Each law |

## 2. External standards and regulators

| # | Item | Why engineering can't close it | What engineering has prepared |
|---|---|---|---|
| E1 | **The Board's interoperability standard** (First Schedule Part A item 9) | Not yet published | Neutral domain model; 27560, FHIR and DEPA as mappings; the Board's standard becomes one more mapping (ADR-025, ADR-030) |
| E2 | **The ISO/IEC TS 27560 text** | Paid; the design used the W3C DPV profile | A JSON schema from the DPV profile, to check field by field against the purchased text (§4 below) |
| E3 | **The ISO/IEC 29184 text** | Paid; the design used published summaries | The notice model (ADR-031); to check against the purchased text |
| E4 | **Health implementation guides** per cell (US Core, ABDM, NPHIES, Malaffi, NABIDH) | Each ecosystem's onboarding and conformance process | FHIR R4 `Consent` mapping and validator in CI (ADR-024) |

## 3. Certification and audit

| # | Item | Who | Evidence the platform produces |
|---|---|---|---|
| C1 | **Independent certification of the consent platform** for registration (Part A item 9) | An auditor the Board accepts | Contracts (ADR-030); conformance tests; the Part B mapping (§6.6) |
| C2 | **The Board audit report** (Part B item 12) | The operating company | A report generated from the ledger, chain verification, access audit and control tests (ADR-023) |
| C3 | **ISO/IEC 27001 and ISO/IEC 27701** for the operating company | Accredited certification body | Asset and data inventories per cell; access logs; key management records; incident runbooks; records of processing for the platform's own data |
| C4 | **HIPAA risk analysis** (§164.308(a)(1)) and BAAs | The operating company, counsel | US cell architecture, safeguards (ADR-024), sub-processor list |
| C5 | **Accessibility conformance** (WCAG 2.2 AA; EN 301 549 report in the EU) | An accessibility auditor | CI accessibility results; manual audit reports (ADR-028) |
| C6 | **Penetration test** to OWASP ASVS L2 (L3 for the vault) | An independent tester | Threat model (§6.9); ASVS mapping |

## 4. ISO/IEC TS 27560 field check

Fill this in from the purchased standard. The fields come from the W3C DPV profile; every field's "Our source"
column says where the platform gets the value.

| Section | Field (DPV profile) | Cardinality | Our source | Checked against ISO text |
|---|---|---|---|---|
| Header | Schema version | 1 | Pinned JSON-LD context version (ADR-030) | ☐ |
| Header | Record identifier | 1..* | Consent record id | ☐ |
| Header | Data subject | 1 | `dpId` (pseudonymous) | ☐ |
| Notice | Notice | 1..* | Notice version id and hash (ADR-031) | ☐ |
| Notice | Notice language | 1..* | Language chosen | ☐ |
| Processing | Purpose | 1..* | Purpose version | ☐ |
| Processing | Personal data / type | 1..* | DPV-mapped categories | ☐ |
| Processing | Sensitive or special category | 0..* | Sensitivity | ☐ |
| Processing | Data controller | 1..* | The fiduciary (and a transferee) | ☐ |
| Processing | Recipients | 1..* | Purpose version recipients | ☐ |
| Processing | Storage condition | 1..* | Retention | ☐ |
| Processing | Geographic restriction | 0..* | Transfers | ☐ |
| Processing | Legal basis | 0..* | `consent` | ☐ |
| Processing | Consent change and withdrawal | 1..* | Withdrawal method | ☐ |
| Processing | Jurisdiction | 1..* | Applicable regimes (ADR-020) | ☐ |
| Processing | Rights | 1..* | From the regime profiles | ☐ |
| Parties | Name, identifier, role (contact and address except for the subject) | per party | Fiduciary profile; the platform; the representative | ☐ |
| Events | Time, type, state, duration, expressed by | per event | Consent events; ADR-026, ADR-028 | ☐ |
| Events | Expression method | 0..1 | Component and version, channel (ADR-028) | ☐ |
| Receipt | Schema version, receipt id, creation time, linked records | 1 each | Receipt (ADR-025) | ☐ |
