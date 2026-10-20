# ADR-026: Consent validity: requested and maximum per purpose, adjustable by the principal

- **Status:** Accepted, implemented by [ADR-044](adr-044-partial-consent-and-validity-at-grant-by-the-principal-only.md) and [ADR-045](adr-045-shorten-withdraw-a-category-renew-and-lapse.md); renewal reminder notifications proposed
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.2, Appendix B finding R9](../compliance/compliance-architecture.md)

## Context

Consent artefacts are valid for a fixed 365 days, set in code (`ApproveCnsCmdHandler.DEFAULT_VALIDITY`), whatever the
purpose. The standards expect something else:

- **ISO/IEC TS 27560:** every consent event must carry a duration. The W3C DPV profile treats endless consent as
  invalid in several jurisdictions, and expects consent to be renewed.
- **HIPAA §164.508(c)(1)(v):** an authorisation must name an expiry date or an expiry event.
- **GDPR Art. 5(1)(e) and DPDPA s.8(7):** data may be kept only as long as its purpose needs. Consent can't cover
  longer than the fiduciary asked for and can justify.
- **DPDPA s.6(4), GDPR Art. 7(3), HIPAA §164.508(b)(5):** the principal may withdraw at any time, for any data
  category, including the ones the fiduciary needs most. DPDPA s.6(5) puts the consequences of withdrawing on the
  principal. ISO/IEC 29184 and GDPR Art. 7(4) require those consequences to be stated clearly.

It was proposed that the principal be able to change a consent's validity, and that the fiduciary name mandatory data
categories in relation to it. The proposal needs a rule for how far each party can go.

## Decision

1. **Every purpose in a consent request carries a validity**, as a date or an event ("until my loan closes"):

   - **`requestedValidity`:** what the fiduciary asks for, used as the default.
   - **`maxValidity`:** the longest the fiduciary can justify for the purpose.

   The request is refused if `maxValidity` exceeds the strictest cap among the applicable regimes (ADR-020; finding
   R3). Endless validity is never accepted. An event-based validity also needs a date backstop, unless the regime
   profile allows an event alone (HIPAA does).
2. **Validity is per purpose, not per data category.** Purpose limitation ties how long data may be kept to the
   purpose, and 27560 puts the duration on the consent event. A fiduciary that needs different durations registers
   separate purposes. Changing which categories a purpose covers is partial consent and partial withdrawal, not a
   validity change.
3. **When granting, the principal may choose:**
   - the requested validity
   - any shorter date
   - a longer one, up to `maxValidity`

   The decision screen opens on `requestedValidity`, never on `maxValidity`. A longer choice is never suggested,
   highlighted or made easier than a shorter one (deceptive design, finding R7).
4. **After granting, the principal may shorten the validity at any time**, for the whole purpose. This is a
   withdrawal scheduled for a date, and it behaves like one:
   - the live status follows the new date
   - tokens never outlive it (finding R1)
   - the fiduciary gets a change webhook
   - a 27560 `modified` event and a new receipt are recorded

   **Extending after granting is possible only by renewal** (27560 "renewed consent given"), up to `maxValidity` from
   the renewal date. The platform prompts the principal before a lapse, and the fiduciary may ask for renewal. A lapse
   is recorded as its own event, the fiduciary is told to stop, and the consent stops verifying.
5. **A fiduciary may mark a data category as `necessary`** for a purpose, with a **consequence statement** shown
   whenever the principal declines or withdraws it ("without your address we can't deliver"). Marking a category
   `necessary`:
   - **doesn't** lock it: the principal may still refuse it, withdraw it, or end the purpose's validity early
   - **doesn't** let the fiduciary decide which categories the principal may adjust
   - **does** let the fiduciary decline to proceed with the purpose without that category, as DPDPA s.6(5)
     contemplates

   Where processing can't depend on the principal's choice (a contract, a legal obligation), consent is the wrong
   basis, and it is outside a pure consent manager (ADR-023). The fiduciary must not ask for consent for it.
6. **The 27560 record holds:**
   - the requested and maximum validity
   - the validity the principal chose and every later change, as events with their duration (`UntilTimeDuration` or
     `UntilEventDuration`)
   - each category's `necessary` flag and consequence text, as part of the notice version the principal saw

## Options considered

- **Fixed validity set by the fiduciary:** simplest, but it gives the principal no say short of full withdrawal, and
  the default fixed period breaks HIPAA and 27560 unless set per purpose anyway.
- **The principal may set any validity, without a maximum:** consent would exceed what the fiduciary asked for and can
  justify, so it would not be specific, and storage limitation would break.
- **Mandatory categories locked against changes:** rejected. It takes away the right to withdraw at any time, and it
  is a deceptive design pattern (withdrawing becomes harder than giving).
- **Validity per data category:** more flexible, but it splits one purpose into several lifetimes. The purpose, not
  the category, is what the law ties duration to. Separate purposes cover the real cases.

## Consequences

- **Every purpose definition needs both validity figures,** and every request is checked against the regime caps.
  Existing artefacts keep their 365-day validity until they lapse or are renewed.
- **The DP portal gains:**
  - a validity choice at grant (bounded, defaulting to the requested validity)
  - "shorten" or "end early" on every consent
  - renewal prompts before a lapse
- **Fiduciaries gain** `modified`, `renewed` and `lapsed` webhooks, and must plan for consents ending earlier than
  requested.
- **Renewal reminders** are notifications to principals that must not themselves nag (finding R7). Their number and
  timing come from the regime profile.

## Compliance impact

- ISO/IEC TS 27560 (duration on every consent event; renewal).
- HIPAA §164.508(c)(1)(v) and (b)(5).
- GDPR Art. 5(1)(b) and (e), 7(3), 7(4).
- DPDPA ss.6(4)-(6) and 8(7).
- ISO/IEC 29184 (consequences of refusing, stated in the notice).
