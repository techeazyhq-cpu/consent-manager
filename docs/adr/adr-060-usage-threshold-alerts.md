# ADR-060: Usage-threshold alerts

- **Status:** Accepted
- **Date:** 2026-11-07
- **Implements:** epic #112; [ADR-039](adr-039-billing-visible-to-fiduciaries-plan-change-requests.md) made usage
  visible, and nothing told anyone before the allowance ran out.

## Context

A fiduciary's plan allows a number of API calls a month. Once it is used up, `cm-cns-cmd-svc` refuses new consent
requests until the month turns. The DF Console shows usage, and CM Admin sorts tenants by how close they are, but a
fiduciary that doesn't open Billing finds out when its requests are refused.

Usage is a Redis counter, `usg:{dfId}:{yyyy-MM}`, that the gateway increments on every authenticated call and batches
reserve from. No code in billing sees it cross a line, so there is no event to react to. Fiduciaries also had no email
channel: `NotifyDfCmdHandler` in `cm-ntf-svc` only logs, and the contact address is held by `cm-df-reg-svc`.

## Decision

1. **A sweep, every minute, in `cm-bil-svc`** (`UsgAlertSweep`). For each ACTIVE subscription it compares this month's
   usage with the plan's monthly allowance, and raises the alert for the **highest** threshold reached: 80% or 100%.
   A fiduciary that jumps from 50% to 100% between sweeps is told it is out, not also that it passed 80%. A suspended
   subscription is skipped (its requests are refused anyway), as is a plan allowing nothing.
2. **Once per fiduciary, month and threshold.** The alert's primary key is `(df_id, period, threshold)`, and raising it
   is an insert that does nothing on conflict. Replicas sweeping at the same moment, a restart, or a counter that
   falls back and rises again raise nothing twice. A plan change mid-month doesn't re-alert: the month's alerts stand.
3. **One transaction.** A new alert is stored with its `UsgThresholdEvt` in billing's outbox and an audit entry
   (`usage.alert`, ADR-040), so none exists without the others. The outbox relay now sends each type to its own topic:
   `bil/evt-platform-action` as before, and `bil/evt-usg-threshold`.
4. **`cm-df-reg-svc` sends the emails.** It consumes `evt-usg-threshold`, looks up the fiduciary, and sends a command
   to a new `cm-ntf-svc` topic, `ntf/cmd-notify-email` (`NotifyEmailCmd`: address, subject, body, reference), which
   `cm-ntf-svc` sends through notification-service. The contact address therefore never leaves `cm-df-reg-svc` except
   in that command: not in an event, and not over an HTTP route (V-11).
   - To the fiduciary's contact address: how much it used, of what, the plan, when it resets, and what happens next
     (new consent requests are refused until then; an Owner or Administrator can ask for a larger plan in Billing).
   - To the platform recipients in `cm.dfreg.usage-alerts.platform-emails` (none by default): the tenant, the numbers
     and the threshold.
5. **Idempotent email.** Each message's reference is built from the alert (`cm-usage-{dfId}-{period}-{threshold}-df`,
   or `-platform-{n}`), so an event delivered twice is one email to the mail service. An unreadable event is dropped;
   a failed send is redelivered.
6. **Seeing them.** `GET /api/bil/me/usage-alerts` (the fiduciary's own, newest first) and
   `GET /api/bil/usage-alerts?period=yyyy-MM` (platform staff, every tenant, this month by default). The DF Console's
   Billing page lists when the fiduciary was emailed; CM Admin's billing overview marks each tenant's highest alert
   and counts the alerted ones.

## Not yet

- **Other channels.** Email only, to the one contact address. Per-member preferences and SMS wait for notification
  channel preferences (epic #106).
- **Other thresholds.** 80% and 100% are constants (`UsgAlert.THRESHOLDS`); making them per plan is a small change.
- **The platform's staff by role.** Platform recipients are a configured list, not "everyone with CM_ADMIN": staff
  addresses live in Keycloak, which `cm-df-reg-svc` doesn't read. CM Admin shows the alerts to every staff member.
- **The per-second limit.** Only the monthly allowance alerts.
- **A reminder before the reset.** An alert is sent once.

## Options considered

- **Alerting where usage is incremented:** the gateway increments a counter with no database or event; adding a check
  there puts a threshold test on the hot path of every call.
- **Putting the contact address in billing** (copied from `DfActivatedEvt`): it would go stale and miss every
  fiduciary activated before the change.
- **An internal `cm-df-reg-svc` route returning the contact address:** another unauthenticated internal route (V-11).
- **Reusing `NotifyDf`:** it has no address and is only logged; a generic email command is also what other services
  will need.

## Consequences

- A fiduciary hears before its requests are refused, and platform staff can see who is about to be.
- Billing sweeps every active subscription each minute: one Redis read each, four at a time.
- A fiduciary with no contact address, or an address the mail server refuses for good, is alerted in the console only.
