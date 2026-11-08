# ADR-084: Billing ends when a fiduciary closes

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #95

## Context

ADR-079 invoices a fiduciary for every month it was on a plan, and left one gap in its own "Not yet": closing a fiduciary
(ADR-064, the end of the wind-down) did not stop its plan stretch, so a closed fiduciary would go on being invoiced every month until
somebody called the billing endpoint by hand. Closing is final, its team can no longer sign in, and nobody would be reading the invoices.

## Decision

1. **The closing pass tells Billing.** In the clean-up that ends a fiduciary's consents, revokes its keys and removes its team,
   cm-df-reg-svc also calls cm-bil-svc's `PUT /internal/dfs/{id}/billing-end`, which closes the fiduciary's open plan stretch from now.
2. **It is the step right after the consents** and before the keys and team, so the closing is stopped by a billing failure before
   anything that cannot be undone is done, and it is tried again on the next pass like every step of the closing (ADR-064).
3. **Ending twice changes nothing:** an already ended stretch stays as it is, so a repeated pass is safe.
4. **The month of closing is invoiced** for the days up to the closing, by the same proration as a plan change. Nothing is refunded
   for the rest of the month it paid for; a month already invoiced is not touched (an invoice is a record: ADR-079).
5. **The endpoint is on the private network,** like the other `/internal/**` calls between services, and is not routed by the gateway;
   the same action for staff is `PUT /api/bil/dfs/{id}/billing-end`, for administrators.

## Not yet / limits

- **Leaving and suspended fiduciaries are billed.** The wind-down is up to 30 days on the plan; stopping billing sooner is a staff
  action. A suspension (ADR-064) is a hold, not an end.
- **No credit for a month already invoiced** when the fiduciary closes inside it; staff issue a credit note if one is due.
- **If Billing is down for long, the closing waits.** That is deliberate: a closed fiduciary that is still billed is worse than one
  that closes a pass later. The closing job logs the failure and retries.

## Options considered

- **An event from df-reg that Billing consumes:** the shape ADR-079's sibling flows use for activation, and right for a design that
  must work when Billing is away. A synchronous call that holds the closing back is simpler and has the failure mode wanted here.
- **Billing asking df-reg which fiduciaries are closed when it invoices:** a call per fiduciary per run, and the stretch would stay
  open in the data.
