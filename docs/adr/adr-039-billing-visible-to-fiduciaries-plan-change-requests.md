# ADR-039: Billing fiduciaries can see, plan changes they ask for, and a platform billing overview

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

cm-bil-svc holds each fiduciary's subscription (plan, status) and counts its usage per calendar month. Plans set
quotas only: requests a month and a second. The platform charges nothing through Consent Manager.

Until now only platform staff could see any of it, one tenant at a time in CM Admin:

- A fiduciary didn't know its plan, its allowance or how close it was to being refused (429).
- To change plan, it had to contact the platform outside the product, and nothing recorded who asked or who decided.
- Staff had no view across tenants: which are about to run out, how many are on each plan.
- cm-bil-svc trusted every caller, relying on the gateway alone, unlike every other service (defense in depth).

## Decision

1. **The fiduciary sees its own billing** (`GET /api/bil/me/subscription`):
   - its plan and the subscription's status
   - this month's usage against the allowance, the per-second limit, and when usage resets (the first of next month,
     UTC)

   The DF Console's **Billing** page shows this to everyone at the fiduciary, with the plans it could move to.
2. **Fiduciaries ask; platform administrators decide.** A plan change request (`PlnChgReq`) runs PENDING, then
   APPROVED, DECLINED or WITHDRAWN.
   - **Owners and Administrators** ask, with an optional note, and can withdraw while the request waits.
   - A fiduciary has **at most one request waiting**; a unique partial index enforces this even for two requests
     arriving together.
   - **Approving changes the plan in the same transaction.** Declining needs a reason, shown to the fiduciary.
   - **Operators** see the queue but don't decide, as with changing a plan directly (ADR-017).
   - Each request records who asked and who decided, and when.

   Fiduciaries don't switch plans themselves, because there's no payment to back a bigger plan.
3. **CM Admin's Billing page:**
   - **Overview:** every tenant's plan, status and usage this month, closest to its allowance first, with totals by
     plan
   - **Plan change requests:** the waiting queue to approve or decline, plus history
   - **Plans:** the catalogue

   A tenant's Subscription tab also shows its waiting request.
4. **cm-bil-svc checks tokens itself,** with the gateway's role rules repeated, like every other service.
   `/internal/**` stays open on the private network for quota checks and batch reservations.

## Consequences

- Fiduciaries can see a refusal coming, and ask for more allowance from inside the product.
- Plan changes have a record of who asked, who decided and why. The audit work recording platform actions in the
  ledger will add it to the tamper-evident trail.
- **Usage counts every call that carries the fiduciary's identity, including its people using the DF Console,** not
  only its systems' API calls. The Billing page says so. Counting only API clients, or only consent requests, is a
  separate decision.
- **No payments and no invoices.** If the platform charges later, plans would need prices and a payment provider, and
  approval could depend on payment.

## Compliance impact

None on personal data: billing holds fiduciary ids, plan names, counts, and staff and member email addresses on
requests.
