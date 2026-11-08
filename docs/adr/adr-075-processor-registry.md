# ADR-075: A fiduciary's register of processors

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #89

## Context

A notice must say who receives the personal data (ISO/IEC 29184; DPDPA s.5), and a purpose version already carries `recipients` and
`transfers` as free text (ADR-031). Free text drifts: the same courier is "Courier Ltd" in one purpose and "courier limited" in
another, a processor the fiduciary stopped using stays in notices nobody revisits, and there is nowhere to say what a processor
does with the data or where. The issue proposed a separate service (`cm-dpr-svc`). The platform is a consent manager (ADR-023),
not a supplier-management tool, so what belongs here is only what a notice needs.

## Decision

1. **A small register inside the consent service,** next to the purposes it serves: each processor has a name, a sentence on what it
   does with the data, the country where it processes it, and a contact. No new service, no cross-service call. Names are unique
   per fiduciary ignoring case and spacing.
2. **Purposes keep naming parties by name.** The register does not replace `recipients` and `transfers`: a version's content stays
   self-contained and immutable once published (ADR-031), so a notice read years later says what it said on the day.
3. **The register is opt-in, with one hard rule: a retired processor cannot be named by anything new.** Creating a purpose, saving a
   draft or publishing one that names a retired processor is refused (409 `processor-retired`); the publication check matters
   because a processor may be retired after a draft was written. Names that are not in the register are still accepted, so a
   fiduciary that has not started one is unaffected and none of the existing purposes change.
4. **Retiring is not deleting.** A retired processor stays in the register, versions that named it stay true, and it can be
   reinstated. Before retiring, `GET .../processors/{id}/uses` lists the purposes whose current version or draft names it.
5. **Changes are audited** (`processor.registered`, `.updated`, `.retired`, `.reinstated`) and need a role that manages purposes
   (ADR-033); reading is open to the fiduciary's people. The register is removed with the fiduciary's other leftovers when the
   retention purge runs (ADR-064).
6. **A page in the fiduciary's console** (Processors) lists the register, adds and describes entries, and warns before retiring
   which purposes still name the processor.

## Not yet / limits

- **No link from a notice to the register.** A principal sees the name written in the notice, not the processor's role or country.
  Showing them needs the register's details frozen into the purpose version at publication, which changes the version's content and
  its hash; it is the next step, not part of this one.
- **The purpose editor does not offer the register's names** while you type, and does not flag a name that is not in it.
- **Matching is exact** (ignoring case and spacing): "Courier Limited" does not match "Courier Ltd".
- **Sub-processors, contracts and data-flow records** are not modelled; they are the fiduciary's own compliance records.
- **The register is not in the offboarding export** (ADR-064); it is configuration, and is removed on purge.

## Options considered

- **A separate `cm-dpr-svc`:** a service, a database schema and a cross-service check on every publication, for a list of a few
  dozen rows per fiduciary.
- **Making the register mandatory** (refuse any name not in it): the right end state, but it would break every existing purpose
  and every integration that creates them, for a benefit the retired-name rule already gives where it matters.
- **Replacing free text with processor ids in versions:** a published version would then change meaning when the processor was
  edited, which the immutability of versions exists to prevent.
