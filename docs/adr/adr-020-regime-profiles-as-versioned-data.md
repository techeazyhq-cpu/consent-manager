# ADR-020: Regime profiles as versioned, reviewed data

- **Status:** Proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.3, Appendix A](../compliance/compliance-architecture.md)

## Context

The regimes differ in figures and switches, and several of those figures are still moving or disputed:

- **Figures that differ:** the age of digital consent, notice languages, the elements a consent must carry, how
  withdrawal must work, deadlines for the platform's own users' rights and grievances, clocks for breaches of the
  platform's own data, retention of consent records, legal holds, transfer rules and who may act for a person. Only
  figures a consent manager needs are held (ADR-023).
- **Still moving or disputed:** DPDP Rules commencing in stages through 2027; Gulf executive regulations whose status
  couldn't be confirmed from official sources.

Hard-coding these figures would put legal judgement in code and slow every correction.

## Decision

1. **One profile per regime** (`DPDPA`, `GDPR`, `UK_GDPR`, `HIPAA`, `UAE_PDPL`, `DIFC_DPL`, `ADGM_DPR`, `KSA_PDPL`,
   `QA_PDPPL`, `BH_PDPL`, `OM_PDPL`): a versioned, effective-dated JSON document in a new library, `cm-reg-kit`, with a
   typed Java API. It is also published as an OPA data bundle, so the PDP applies the same rules.
2. **Code asks the profile; it never states a figure.** For example `profile(KSA_PDPL, at).rightDeadline(ACCESS)`.
3. **Every entry carries `source` and `confirmed`.** A profile change is a pull request owned by the DPO (CODEOWNERS).
   CI fails an entry without a source, and production refuses to start on a profile with unconfirmed entries the
   deployment relies on.
4. **Effective dating:** a request or incident is governed by the profile version in force when it was received.
   That version is recorded on it.
5. **Profiles are as fine-grained as the law** (finding R3).
   - **GDPR:** a base profile with an overlay per member state (`GDPR_DE`, `GDPR_FR`, …) for its derogations, such
     as the digital-consent age of 13 to 16. `UK_GDPR` is separate.
   - **UAE:** federal law, DIFC and ADGM are separate profiles, chosen by the tenant's establishment.
6. **Each consent has a set of applicable regimes**, not one (finding R3). The set is derived from:
   - the fiduciary's establishment (its tenancy)
   - the principal's residence (held in the vault) or the markets the purpose targets
   - the purpose's sector (health makes HIPAA apply in the US cell)

   Rules compose **strictest wins**, item by item:
   - the higher age
   - the shorter maximum validity and token lifetime
   - the longer record retention where a legal hold requires it, otherwise the shorter
   - fail-closed over fail-open

   The set and the profile versions used are recorded on the request and on the 27560 record (Jurisdiction 1..*). A
   consent whose set includes a profile with unconfirmed entries it relies on is refused, not guessed.

## Options considered

- **A rules service with an admin UI:** faster edits, but legal figures would change without review or history. A
  repository with review is the stronger control, and changes are rare.
- **Constants in each service:** rejected for the reasons in Context.

## Consequences

- Counsel can correct a figure without touching code, and every decision can be traced to the figure and source it
  used.
- A figure change still needs a release, an accepted delay for a reviewed change.

## Compliance impact

Accountability and demonstrability (GDPR Art. 5(2), DPDPA s.8), with one source of truth for each regime's rules.
