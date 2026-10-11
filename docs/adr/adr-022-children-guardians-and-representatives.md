# ADR-022: Children, guardians and other representatives

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.4](../compliance/compliance-architecture.md)

## Context

The regimes let, or require, someone else to act for a person:

- **DPDPA:** a parent or lawful guardian for a child (under 18) or for a person with a disability, with verifiable
  consent (s.9, Rule 10). DPDPA also lets a principal nominate someone to act on their death or incapacity (s.14).
- **GDPR:** parental authorisation below the digital-consent age, which is 16 unless a member state lowers it (Art. 8).
- **HIPAA:** a personal representative (§164.502(g)).
- **The Gulf laws:** have their own capacity rules **[confirm]**.

DPDPA also forbids tracking, behavioural monitoring and targeted advertising directed at children (s.9(3)).

## Decision

1. **A `Representation` in the Identity Vault** (ADR-019): representative, subject, type (`PARENT`, `GUARDIAN`,
   `NOMINEE`, `PERSONAL_REPRESENTATIVE`), legal basis, scope, validity, verification method and evidence reference.
2. **A consent request for a subject under the regime's digital-consent age** (ADR-020), or with an active guardian,
   goes to the representative. The artefact records the representation and its verification.
3. **Accepted verification methods are listed per regime profile.** For DPDPA: identity and age details already held,
   a virtual token from an authorised entity, or a DigiLocker credential **[confirm against Rule 10]**. Evidence is
   held by reference, never as a copy of an identity document.
4. **Purpose definitions carry flags** (`TRACKING`, `BEHAVIOURAL_MONITORING`, `TARGETED_ADVERTISING`). A request for
   a child under DPDPA is refused if any is set, with the exemptions the profile lists.
5. **Nominees act only once their trigger is recorded** (death or incapacity), with evidence.

## Consequences

- One model covers four regimes' representatives.
- Age verification needs a trusted source. Self-declared age is only acceptable where the profile allows it.
- The DP portal needs a "you are acting for…" mode.

## Compliance impact

DPDPA ss.9 and 14 and Rule 10, GDPR Art. 8, HIPAA §164.502(g), and the Gulf laws' capacity rules.
