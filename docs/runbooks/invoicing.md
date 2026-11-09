# Runbook: invoicing

[ADR-079](../adr/adr-079-invoicing.md). Admin console: Billing, then Invoices.

## Set up (once)

1. Settings (Helm `config.billing`, or `CM_BIL_CURRENCY`, `CM_BIL_TAX_LABEL`, `CM_BIL_TAX_BPS`): the currency, and the tax rate in
   basis points (GST 18% is `1800`). Changing them affects invoices issued afterwards, not earlier ones.
2. Prices: Billing, Invoices, *Monthly price of each plan*. Prices are in major units there (`2999.00`) and stored in minor units.
   Every plan starts at 0, so nothing is invoiced until a price is set. **Set a price before the first of a month**; invoices use the
   price in force when they are issued.

## Each month

Invoices for the month that just ended are issued automatically within an hour of it ending. To check, or to do it by hand:
Billing, Invoices, pick the month, *Issue this month's invoices*. It reports how many were new; running it again adds none.

- Mark an invoice paid when the money arrives (*Mark paid*, with the reference). **Do it promptly:** an invoice that is not marked
  paid is reminded about by email (below), and nothing else says it was paid.
- To correct a wrong invoice, *Credit* it with a reason. That issues a credit note for the full amount and marks the invoice credited.
  If the fiduciary should still be charged, change what is wrong (a price, a plan) and issue a later invoice by hand; an invoice
  cannot be edited.
- *Download CSV* gives the month's invoices and credit notes for the accounts.

## Reminders about unpaid invoices

An invoice falls due 14 days after it is issued (`config.billing.paymentTermsDays`). Every hour Billing looks for invoices past their
due date that are not paid or credited, and each reaches the steps in `config.billing.reminderDays` (3, 10 and 20 days after the due date
by default): the fiduciary's contact address gets one email at each step ([ADR-105](../adr/adr-105-invoice-reminders.md)), saying which
invoice, how much and since when. If the job was down, only the latest step is sent, once. **Marking the invoice paid, or crediting it,
stops them.** Nothing is sent for a credit note, or for an invoice of nothing. An unpaid invoice does not suspend anyone.

- To change the timing, set `config.billing.paymentTermsDays` and `reminderDays`; steps already sent are not sent again. `remindersEnabled: false`
  stops them all.
- To see what was sent: the audit trail has `invoice.reminder` with the invoice number and the step, and `bil.invoice_reminder` has the
  rows. The email's text is in the mail service's log under the reference `cm-invoice-<invoice id>-<step>`.
- A fiduciary that says it has paid: check, mark the invoice paid with its reference, and the reminders stop.
- The log says `CM-INVOICE-REMINDER-FAILED` when one could not be raised; it is tried again within the hour.

## When a contract ends

Closing a fiduciary ends its billing by itself (ADR-084): the closing pass tells Billing, and no month after it is invoiced. A
fiduciary that is *leaving* (the wind-down, up to 30 days) is still billed until it closes; to stop billing sooner call
`PUT /api/bil/dfs/{id}/billing-end` (administrators). The month it closes in is invoiced for the days it was on its plan.

## If the run fails

The log says `CM-INVOICE-RUN-FAILED`. Invoices already issued stay. Check the billing database and that every plan has a price, then
run the month by hand. A gap in invoice numbers means something is wrong: tell engineering, do not renumber.

## What a fiduciary sees

Its own invoices and credit notes in Billing, with a *View or print* page. Another fiduciary's are never visible to it.
