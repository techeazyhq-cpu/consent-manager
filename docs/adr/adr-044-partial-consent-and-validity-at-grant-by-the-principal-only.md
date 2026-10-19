# ADR-044: Partial consent and the validity chosen at grant, by the principal only

- **Status:** Accepted
- **Date:** 2026-10-20
- **Implements:** [ADR-026](adr-026-consent-validity-requested-maximum-principal-adjustable.md) §1, §3 and §5; the
  grant side of partial consent ([compliance architecture §6.2](../compliance/compliance-architecture.md))

## Context

Every consent covered everything the request asked, for a fixed 365 days. ADR-026 decided that the principal chooses
at grant: which of the asked categories, and a validity that opens on what the fiduciary requested and can't exceed
its maximum. With purpose versions (ADR-043), each request now has those figures.

While building this, we found that `approve`, `deny` and `revoke` checked only that the caller was signed in. A
fiduciary could raise a request for a principal's `dpId` and approve it with its own API key (V-15).

## Decision

1. **Only the principal decides.**
   - Approve, decline and withdraw take the principal from the caller's token, never from the body.
   - A caller who isn't a principal gets 403 `principal-only`.
   - A principal who isn't the one the record names gets 404, so the record's existence isn't given away.
   - Ending a consent by a fiduciary or an authority is a separate, recorded act (ADR-029), never a withdrawal on the
     principal's behalf.
2. **The grant names categories and a validity** (`CnsGrant`):
   - `dCatCodes`: a non-empty subset of what the request asked. Anything else is refused (400 `invalid-grant`).
   - `validityDays`: from 1 to the purpose version's maximum.
   - Either may be left out: everything asked, for the requested validity.
   - A request stored without a purpose version keeps the old 365 days.
3. **A `necessary` category can still be refused** (ADR-026 §5). The decision screen shows its consequence, plainly
   once it is unticked. The fiduciary may then decline to proceed; the platform doesn't stop the principal.
4. **What was granted is recorded everywhere the consent is:**
   - the artefact's scope, which is the consent token's `scope` claim
   - `CnsGrantedEvt` (`scp`, `validityDays`)
   - the read model's `grantedDCatCodes`, which existing grants get as everything asked
5. **The decision screen** (DP portal) loads the request's terms from `GET /api/cns-cmd/cns-reqs/{id}/terms`, which only
   the principal and the requesting fiduciary can read.
   - No category is ticked to start with.
   - The validity is a date, opening on the requested validity, between tomorrow and the maximum.
   - Declining stays one click.
   - A consent given in part shows the categories not shared.

## Options considered

- **Pre-tick the needed categories:** faster, but a pre-ticked box isn't an affirmative act (GDPR recital 32;
  ADR-028), and it nudges toward sharing.
- **Validity per category:** rejected in ADR-026; categories are granted or not, for one validity per purpose.
- **Keep approve open and filter in the gateway:** the service is the authority on who owns a record. A gateway rule
  wouldn't know the request's principal.

## Consequences

- Fiduciaries must read `grantedDCatCodes`, or the token's `scope`, and use only those categories.
- Giving consent takes a tick per category, while declining stays one click. Accepting is never made easier than
  refusing.
- Shortening, withdrawing a category, renewal and lapse come next, as does a 27560 record of each grant (ADR-025).
