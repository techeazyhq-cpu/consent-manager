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

- Mark an invoice paid when the money arrives (*Mark paid*, with the reference).
- To correct a wrong invoice, *Credit* it with a reason. That issues a credit note for the full amount and marks the invoice credited.
  If the fiduciary should still be charged, change what is wrong (a price, a plan) and issue a later invoice by hand; an invoice
  cannot be edited.
- *Download CSV* gives the month's invoices and credit notes for the accounts.

## When a contract ends

Closing a fiduciary ends its billing by itself (ADR-084): the closing pass tells Billing, and no month after it is invoiced. A
fiduciary that is *leaving* (the wind-down, up to 30 days) is still billed until it closes; to stop billing sooner call
`PUT /api/bil/dfs/{id}/billing-end` (administrators). The month it closes in is invoiced for the days it was on its plan.

## If the run fails

The log says `CM-INVOICE-RUN-FAILED`. Invoices already issued stay. Check the billing database and that every plan has a price, then
run the month by hand. A gap in invoice numbers means something is wrong: tell engineering, do not renumber.

## What a fiduciary sees

Its own invoices and credit notes in Billing, with a *View or print* page. Another fiduciary's are never visible to it.
