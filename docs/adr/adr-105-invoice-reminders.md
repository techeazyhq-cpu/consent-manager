# ADR-105: Reminders for unpaid invoices

- **Status:** Accepted
- **Date:** 2026-11-09
- **Issue:** #95
- **Builds on:** [ADR-079](adr-079-invoicing.md), [ADR-060](adr-060-usage-threshold-alerts.md) (the same event path)

## Context

ADR-079 issues invoices and lets staff mark them paid, but nothing says an invoice has gone unpaid: staff have to remember to look,
and a fiduciary that overlooked an invoice hears nothing until someone writes to it. There is no payment gateway, so "paid" is a
person marking it. Reminders are the part of the issue's "dunning" that needs no gateway and no credentials.

## Decision

1. **An invoice falls due** `paymentTermsDays` after it is issued (default 14, `cm.bil.payment-terms-days`). It is not stored: the due
   date is the issue date plus the terms, so changing the terms applies to invoices already issued.
2. **An unpaid invoice is reminded about at each of `reminderDays`** after it falls due (default 3, 10 and 20, `cm.bil.reminder-days`; blank, or
   `remindersEnabled: false` in the chart, means none). Only the latest step reached is raised, once: a sweep that was down for a week
   sends one reminder, not three. After the last step nothing more is sent.
3. **An hourly sweep in cm-bil-svc** reads the invoices (kind INVOICE, status ISSUED, something to pay) issued far enough back to have
   reached the first step, works out each one's step, and raises it. A step is recorded in `bil.invoice_reminder`, keyed by invoice and
   step, in the same transaction as the event and an audit entry (`invoice.reminder`), so replicas sweeping together raise it once.
   One invoice failing is logged (`CM-INVOICE-REMINDER-FAILED`) and does not stop the others.
4. **Billing says so in an event; cm-df-reg-svc sends the email.** `evt-invoice-reminder` carries ids, the invoice number, the amount, the
   currency, the due date and the step, never an address. cm-df-reg-svc holds the fiduciary's contact address and sends the email through
   cm-ntf-svc, with a reference built from the invoice and step so a redelivered event is one email. This is the path ADR-060's usage
   alerts already take.
5. **Paying or crediting stops them** because the sweep only looks at issued, unpaid invoices. Credit notes and invoices of nothing are not
   looked at. An unpaid invoice does not suspend anyone (ADR-079).
6. **The message** says which invoice, how much and since when it was due, and what to do: if it is paid, send the reference so it can be
   recorded; if not, see it in the console under Billing. It is calm and the same at every step.

## Checked

- Unit tests: the policy (due, each step, the latest reached, none before the first, invalid input), the sweep (not yet overdue, once however
  often swept, each later step once, only the latest after downtime, one failure not stopping the rest, no steps), and the email.
- Four integration tests against PostgreSQL: which invoices are looked at (paid, credited, zero, credit note, too recent excluded), a paid
  invoice dropping out, eight replicas raising a step once with one event, one audit entry and no address in the payload, and steps being
  independent.
- Live, with the rebuilt billing and fiduciary-registry services: the migration ran, and for a test invoice issued 20 days earlier (6 days
  past due) the sweep recorded the step-3 reminder, with its event and audit entry in the outbox. **The rest of the path was not seen
  working:** the local Pulsar's embedded BookKeeper was damaged (`Bookie handle is not available`, which also broke the notification
  service's own publishing; a restart did not fix it, and repairing it means deleting its data), so the relay could not publish and the
  email from cm-df-reg-svc was never sent. That leg rests on its unit tests and on being the path ADR-060's usage alerts already use.
  The test invoice and its rows were removed afterwards.
- The chart passes the terms and steps (and none when off), checked in CI.

## Not yet / limits

- **No payment gateway:** "paid" is a person marking it, so a fiduciary that has paid and is not yet marked gets a reminder. The email says what to do.
- **The same message at every step, and no escalation** (to the platform's staff, to a suspension); that needs a decision about what a final
  reminder should lead to.
- **The fiduciary's contact address only,** not each Owner; the address is the one it registered with.
- **Amounts are shown with two decimals,** which is right for rupees, dollars and euros and wrong for a currency with none or three.
- **The email text is English** and not configurable.
- **Reminders are per invoice:** a fiduciary with three unpaid invoices gets three emails.
- **A reminder is raised when the sweep first sees the step,** up to an hour after the days have passed.
