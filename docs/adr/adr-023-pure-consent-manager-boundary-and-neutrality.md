# ADR-023: A pure consent manager: its boundary, neutrality and First Schedule duties

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §1, §6.6](../compliance/compliance-architecture.md)

## Context

DPDPA s.2(g) defines a consent manager as the principal's single point of contact to give, manage, review and
withdraw consent through an accessible, transparent and interoperable platform. The DPDP Rules' First Schedule sets 13
obligations (Part B). Among them:

- the consent manager can't read the personal data flowing through (B2)
- it may not subcontract its obligations (B6)
- it acts in a fiduciary capacity towards principals (B8)
- it must avoid conflicts of interest with fiduciaries (B9, B10)
- it must disclose its ownership publicly (B11)
- it must report audits to the Board (B12)

Registration needs independent certification that the platform meets the Board's interoperability standards (Part A
item 9).

An earlier draft of this design added privacy-operations features for tenants:
- breach registers
- records of processing
- non-consent lawful bases
- a general rights-fulfilment hub

Those are a fiduciary's or controller's duties. Taking them on would blur the consent manager's role and put it in the
path of fiduciaries' data.

## Decision

1. **The platform is a consent manager only.**

   **It does:**
   - shows fiduciaries' notices
   - runs the consent lifecycle for each purpose (including sharing between onboarded fiduciaries, consent on behalf
     of others, and HIPAA authorisations)
   - keeps tamper-proof records and issues receipts (ADR-025)
   - verifies consent and tells fiduciaries of changes
   - meets its own duties as a data fiduciary for its users' account data (ADR-021)

   **It does not:**
   - process, store or route governed data
   - record lawful bases other than consent
   - keep fiduciaries' records of processing, DPIAs, sub-processor or transfer registers
   - run fiduciaries' breach responses
   - fulfil rights requests against fiduciaries' data (it relays them)
2. **Data-blind by construction (B2):** no API accepts governed data. Tokens, webhooks and records carry ids, purposes
   and categories, never values. A CI fitness function fails any API schema that does.
3. **Neutral (B8 to B10):**
   - fiduciaries get no data about principals beyond their own consents
   - no analytics, ranking or advertising on principals
   - billing never depends on consent outcomes
   - staff declare conflicts of interest, kept in `cm-stf-svc`
4. **Transparent (B11, B12):**
   - a public disclosure page, generated from a reviewed disclosure file
   - a periodic Board report, generated from the ledger and controls evidence
5. **No subcontracting of obligations (B6):** every obligation is met by the platform's own services. Hosting, message
   delivery and timestamping vendors act as processors under contract, not as delegates of an obligation **[confirm
   with counsel]**.
6. **Its own incidents only:** the platform responds to breaches of its own data with an incident runbook using the
   regime profile's clocks (ADR-020). There is no tenant breach register.
7. **Its own duties as a data fiduciary or controller.** For what it holds about its users (account, contact points,
   representatives, consent records), the platform is itself a data fiduciary or controller. So:
   - **Its own privacy notice** (DPDPA s.5; GDPR Arts. 13-14), shown at sign-up and kept in the DP portal, in the
     principal's language. It states:
     - what it holds and why
     - its lawful basis: its contract with the user, and its legal obligations (the consent-record retention of B4, and
       HIPAA documentation); never consent to its own core service
     - retention and legal holds
     - recipients: its processors for hosting and delivery
     - rights, and how to complain
   - **A published contact** for principals' questions about its own processing (DPDPA s.8(10)): a grievance officer
     or DPO. It appears on the disclosure page (B11), in every receipt, and in the platform's own notice.
   - **Its own records of processing** for that data (GDPR Art. 30 as a controller), kept with its ISO/IEC 27701
     management system. These are records about the platform, not a service to tenants.

## Options considered

- **Consent manager plus privacy-operations suite for tenants:** more to sell. Rejected: these are fiduciaries'
  duties; they would raise conflict-of-interest questions (B9) and widen what the platform holds.

## Consequences

- The product does one thing, which is what registration certifies and what principals can trust.
- Fewer services than the earlier draft (no governance service).
- Tenants needing privacy-operations tooling must use other products. The platform's webhooks and receipts feed them.

## Compliance impact

DPDPA s.2(g) and s.6(7)-(9); DPDP Rules First Schedule Parts A and B; GDPR Art. 7 (the platform proves consent for
controllers); HIPAA §164.508 (authorisations only).
