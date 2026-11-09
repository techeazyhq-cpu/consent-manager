# ADR-079: Monthly invoices, proration and credit notes

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #95

## Context

Billing (ADR-039) meters API usage against a plan's quota and lets a fiduciary ask for a different plan, but plans have no
price, nothing records which plan a fiduciary was on when, and there is no invoice. A platform that charges needs a monthly
statement per fiduciary that adds up correctly when a plan changes mid-month, can be corrected without rewriting history, and can
be handed to accounts. The payment gateway, dunning and multi-currency parts of the issue need decisions and credentials that do
not exist yet; this ADR covers what does not.

## Decision

1. **Plans have a monthly price** in minor units (paise, cents), set by an administrator, per plan. The platform has one
   currency (`cm.bil.currency`, ISO 4217, default INR) and one tax rate (`cm.bil.tax-bps`, basis points, default 0, with a label
   such as GST) that is shown as its own line. Quotas stay hard limits: there is no overage charge.
2. **Which plan, when, is recorded** (`bil.plan_hst`): a stretch opens when a subscription starts and closes and reopens when its
   plan changes. It is written where the subscription is saved, so no caller can change a plan without it. For subscriptions
   that existed before this change, the history is the plan they have now, from when they began: earlier changes are not known.
3. **An invoice is a calendar month in UTC, issued after the month has ended,** one per fiduciary. Each stretch on a priced plan is
   charged in proportion to the seconds it covers: a whole month is exactly the price, a plan changed on day 11 of 30 charges 10/30
   of one price and 20/30 of the other, each line rounded to the minor unit. A fiduciary with nothing to pay gets no invoice.
4. **It runs by itself and by hand.** A job looks every hour and issues last month's invoices; an administrator can run any ended
   month from the console. Running a month twice issues nothing twice: a fiduciary has one invoice per period (a unique key, and a
   lock so two replicas that start together take one number between them).
5. **Numbers have no gaps,** `INV-2026-000007`, sequential within kind and year, allocated in the same transaction as the invoice.
6. **Issued invoices are records.** A database trigger refuses any change to an invoice but its status (ISSUED, PAID, CREDITED) and
   payment fields, any change to its lines, and any deletion. A mistake is corrected by a **credit note** (`CN-...`) that reverses
   the invoice in full with the same lines negated and a reason; an invoice is credited once. Billing again after that is a new
   invoice in a later period, or a manual matter.
7. **No payment gateway:** staff mark an invoice paid with a reference. A fiduciary reads and prints its invoices in the console
   (Billing page); staff see everyone's, can download the accounting CSV for a month, and set prices.
8. **Billing can be ended** for a fiduciary (`PUT /api/bil/dfs/{id}/billing-end`): its open stretch is closed, so months after that
   are not invoiced. Closing a fiduciary calls it ([ADR-084](adr-084-billing-ends-when-a-fiduciary-closes.md)); staff can also use it by hand.

## Not yet / limits

- **No payment gateway,** and an unpaid invoice does not suspend anyone. Reminders for an unpaid invoice are in [ADR-105](adr-105-invoice-reminders.md).
- **One currency and one tax rate for the platform.** Tax by the fiduciary's country (GST, VAT, reverse charge) and invoicing a
  fiduciary in its own currency need a country on the tenant and a rates table.
- **No tax registration number, address or PDF** on the invoice: the printable page is HTML a person prints from a browser.
- ~~**Closing a fiduciary does not end its billing**~~ It does now (ADR-084), and so does starting to leave (ADR-109). A fiduciary that is only suspended is still billed.
- **The history before this change is approximate** (decision 2), so the first invoices of a fiduciary that changed plans earlier
  charge its present plan for the whole time.
- **Prices apply as they are when the invoice is issued,** not as they were during the month. Change a price before the first of
  a month, not in the middle of one, or accept that the month is charged at the new price.
- **Suspension is billed:** a suspended fiduciary is still on its plan.

## Options considered

- **Billing from the subscription's `cur_period` fields:** they are fixed at 30 days from activation and never roll, so they do not
  mark billing periods.
- **Overage charges:** the quotas are enforced limits (ADR-016), so there is nothing beyond them to charge for.
- **Computing invoices on request, never storing them:** a price change would rewrite what a fiduciary was told it owed.
- **A sequence object for numbers:** gapless is not what database sequences give; a table row locked in the issuing transaction is.
