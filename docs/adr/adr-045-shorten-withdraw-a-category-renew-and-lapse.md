# ADR-045: Shortening, withdrawing a category, renewing, and lapse

- **Status:** Accepted
- **Date:** 2026-10-20
- **Implements:** [ADR-026](adr-026-consent-validity-requested-maximum-principal-adjustable.md) §4; the change side
  of partial consent (ADR-026 §2)

## Context

After ADR-044 a principal chooses which categories to give, and for how long. Once given, the only change was to
withdraw everything. ADR-026 §4 lets the principal shorten a consent at any time and extend it only by renewal. It
also requires a lapse to be recorded, with the fiduciary told to stop. ISO/IEC TS 27560 names these states
*modified*, *renewed* and *lapsed*.

## Decision

1. **Three changes, by the principal only** (`POST /api/cns-cmd/cns-arts/{id}/…`), with the same rules as approval
   (ADR-044): a fiduciary gets 403 `principal-only`, another principal 404.
   - **`shorten {until}`:** the consent ends earlier. The new end must be after now and before the current one. This
     is a withdrawal scheduled for a date: the live status, and so the PEP, follows it at once (ADR-027).
   - **`withdraw-dcat {dCatCode}`:** stop sharing one category; the rest stays. The last category can't be withdrawn
     this way: that is withdrawing the consent, recorded as such.
   - **`renew {validityDays}`:** a new validity from now, up to the purpose version's maximum. It must end later than
     the consent does now. Ending sooner is shortening, not renewal.
2. **Only a consent in force changes.** A withdrawn or lapsed consent is refused with 409 `cns-art-not-in-force`. A
   lapsed consent isn't renewed: the fiduciary asks again with a new request.
3. **Each change is an event:**
   - `CnsModifiedEvt` (`SHORTENED`, or `CATEGORY_WITHDRAWN` with the category) and `CnsRenewedEvt` carry the
     consent's new scope and validity, the request and the fiduciary.
   - The ledger records them, attributed to the person.
   - The read model updates `grantedDCatCodes` and the validity.
   - The history gets an entry whose `chg` names the change, since the status stays `ISSUED`.
   - Webhooks to the fiduciary (ADR-027 §6.3) will carry the same events.
4. **A lapse is recorded where the fiduciary can see it.** The expiry sweep already marked a consent `EXPIRED` (27560
   "lapsed") once its validity ended. `CnsExpiredEvt` now also names the request, the fiduciary and when it lapsed,
   so the ledger attributes it. Verification refuses a lapsed consent from its end, whether or not the sweep has run.
5. **Renewal is prompted, not pushed.** A consent ending within 14 days shows its end on the card, opens its change
   panel ready to renew, and is counted on the dashboard. Reminder notifications, whose number and timing come from
   regime profiles (ADR-020), come later and must not nag (ADR-028).

## Options considered

- **Extend instead of renew:** stretching the current period would let consent outlast the maximum counted from
  when it was given. Renewal is a fresh, recorded decision, as 27560 models it.
- **Shorten by a number of days:** a date is what people think in. The new end keeps the time of day of the old
  one, so "a week sooner" is exactly a week.
- **Allow withdrawing the last category:** a consent covering nothing would still verify. Withdrawing it is clearer
  and is already one click.

## Consequences

- Tokens never outlive a shortened consent: a refreshed token's `exp` is capped by the new end, and the live check
  refuses it from then. The token's `scope` follows a withdrawn category on refresh.
- Fiduciaries must expect consents to end earlier than they asked, and to narrow, as ADR-026 said.
- Reminder notifications and webhooks remain to be built. So does the 27560 record of each event (ADR-025).
