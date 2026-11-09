# ADR-109: Billing ends when a fiduciary starts leaving

- **Status:** Accepted (the rule was proposed by engineering and agreed by the maintainer on 2026-11-09)
- **Date:** 2026-11-09
- **Issue:** #95
- **Builds on:** [ADR-084](adr-084-billing-ends-when-a-fiduciary-closes.md), [ADR-064](adr-064-fiduciary-offboarding.md)

## Context

ADR-084 ended a fiduciary's billing when it *closed*. Before that, a fiduciary that asked to leave (or had its contract ended by two members
of staff) entered a wind-down of up to 30 days (ADR-064) in which its API keys are refused, no new key can be issued, and nothing new is
accepted: it can read its records and nothing else. It was billed for that month anyway, "until it closes". It is hard to defend charging for a
service the fiduciary cannot use.

## Decision

1. **Billing ends the moment a departure begins,** by the Owner's request or by two members of staff (ADR-064 D7). cm-df-reg-svc tells
   cm-bil-svc (`PUT /internal/dfs/{id}/billing-end`, as the closing does) right after it saves the departure. The month it begins in is
   invoiced for the days before it, by the same proration as any plan change; the wind-down is not invoiced.
2. **A departure that is called off bills it again.** Cancelling (by the Owner's one cancellation, or by staff) calls a new
   `PUT /internal/dfs/{id}/billing-resume`, which opens a plan stretch from now on the plan the subscription has. **The days in
   between stay unbilled:** it could not use the platform then. Resuming for a fiduciary that is still billed, or twice, changes nothing.
3. **Suspension does not end billing.** A suspension is a hold, and ends or becomes a departure; the fiduciary keeps its place and its plan.
   (A suspended fiduciary that then leaves has its billing ended when it leaves.)
4. **Ending is best effort, resuming is not.** If Billing cannot be reached when a departure begins, the departure still begins, with a warning
   (`CM-LEAVING-BILLING-FAILED`): the closing that ends the wind-down ends billing too (ADR-084), so the worst case is the billing it had
   before this change, and staff can end it by hand. Cancelling a departure fails if billing cannot resume, in the same transaction, so
   the cancellation is not kept and the Owner can try again: a fiduciary never comes back unbilled without anyone knowing.
   **Billing is waited for briefly,** because the request is the gateway's, which gives up at 10 seconds: 3 seconds to end billing (then the
   departure goes ahead) and 5 to resume it (then the cancellation fails with `billing-unavailable`, a 503 the Owner can retry). A first
   version waited 15 seconds and found out why that is wrong: see the live check.
5. **Staff can resume by hand** with `PUT /api/bil/dfs/{id}/billing-resume` (administrators), as they can end it.

## Checked

- Unit tests (cm-df-reg-svc): billing is told once when an Owner leaves and when staff's two-person departure is approved, and not on a
  proposal or a wrong name; a billing failure does not stop the person leaving, and the team is still emailed; cancelling by the Owner and by
  staff each resume billing, and a failure to resume reaches the caller.
- Seven integration tests against PostgreSQL (cm-bil-svc): ending closes the open stretch and resuming opens one on the plan the subscription has now; the days
  between are not in the segments the invoice is worked out from; repeats change nothing; a fiduciary still billed, or without a subscription, gets
  nothing; eight resumes at once open one stretch.
- **Live, on the local stack** (the rebuilt billing and fiduciary-registry services), with `tests/security/e2e-leaving.mjs` extended to
  read billing's plan stretches from the database. All checks pass: an active fiduciary has one open stretch; when the Owner leaves it has none
  (one closed stretch); when the departure is cancelled it has one open and two in all (the days between unbilled); leaving again closes it
  again; and the earlier checks of the flow (the wrong name, the API key refused, the team emailed, the one cancellation) still pass.
- **Live, with billing stopped** (the container down): the Owner's departure **still began** (200, leaving) with billing left on; calling it
  off returned **503 `billing-unavailable`**, the fiduciary was **still leaving** and its one cancellation **still available**; with billing back,
  the cancellation went through (200, active). **The first version of this change failed that check:** with billing stopped, its call waited
  15 seconds, the gateway gave up at 10 and the whole leave request was cancelled and not kept (a 503 `df-reg is unavailable`), which a
  unit test with a billing that fails at once could not show. The waits above are the fix, and two unit tests with a billing that never answers
  now cover them.

## Not yet / limits

- **A saved subscription can re-open billing.** The subscription's save re-opens a plan stretch if none is open (ADR-079, so that a plan
  assignment always starts one). Anything that saves a leaving fiduciary's subscription after billing ended (a plan assignment or a subscription status change by staff, for example) would
  open it again from that moment. Nothing in the leaving flow does, and the closing ends it again, but it is a way for a leaving fiduciary
  to be billed that this does not close.
- **The days already invoiced are not credited.** Invoices are issued for a month after it has ended, so a month is never invoiced
  before a departure that falls inside it; if staff invoiced a month by hand early, a credit note is theirs to issue (ADR-084).
- **No refund of an earlier payment.** Billing is monthly in arrears; there is nothing paid ahead.
- **Suspended fiduciaries are still billed,** by decision 3. If that should change, it is a different decision: a suspension may be a
  dispute about money, and not billing for it needs a limit.
- **If Billing is down when a departure is cancelled, the Owner sees an error** and has to try again later; the message is the general
  "a service is unavailable".
