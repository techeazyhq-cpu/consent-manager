# ADR-040: Audit every change on the platform, with who made it

- **Status:** Accepted
- **Date:** 2026-10-19
- **Extends:** [ADR-014](adr-014-tamper-evident-audit-ledger.md) (the ledger) and [ADR-015](adr-015-ledger-checkpoints-anchored-by-a-timestamp-authority.md)
  (its anchored checkpoints)

## Context

The tamper-evident ledger (cm-aud-svc) recorded the consent lifecycle and fiduciary activation, and nothing else.

- **Not recorded:**
  - registering a tenant, changing its logo
  - plan changes, suspensions and plan change requests
  - inviting, re-roling and removing a fiduciary's people
  - adding, enabling and disabling platform staff
  - lists sent to many people
  - notification delivery failures
- **No actor:** entries named the fiduciary or principal concerned, not the person or system who acted.
- **No screen:** neither CM Admin nor the DF Console showed the ledger.

DPDPA, GDPR and SOC 2/ISO 27001-style evidence all expect an answer to "who changed this, and when" for
administrative actions, not only for consent.

## Decision

1. **Every state change a person or system makes is a `PlatformActionEvt`** (cm-aud-kit):
   - `ctx`, `action` (noun.verb, such as `plan-change.approved`), `dfId`, `target`
   - the `actor`: STAFF, DF_MEMBER, DF_SYSTEM, DP or SYSTEM, with an id and a name
   - `details`: short facts for people reading it

   Details never hold a data principal's personal data: plan names, roles, counts, and staff or member email
   addresses.
2. **Who acted comes from the request, not the command.** A web filter in cm-sec-kit (auto-configured in every
   service) turns the caller's token into an `Actor` in Reactor's context. Application code calls
   `AuditTrail.record(action, dfId, target, details)` without passing the actor through. Scheduled jobs and event
   consumers act as SYSTEM.
   - Principals are named by their id, never their email.
   - Staff and members are named by their email, and API clients by their client id.
3. **Recorded with the change, through the outbox** (ADR-004).
   - df-reg, cns-cmd and bil write the action in the same transaction as the change. bil gains an outbox for this.
   - cm-stf-svc has no database, as Keycloak holds staff accounts, so it publishes directly with retries. If
     publishing still fails, it logs the event in full for replay rather than failing a change that has already been
     made.
   - Each context publishes to `persistent://cm/<ctx>/evt-platform-action-v1`. The ledger subscribes for dfreg, bil, stf
     and cnscmd, and also takes ntf's `dp-delivery-failed`.
4. **Recorded actions:**

   | Context | Actions |
   |---|---|
   | dfreg | `tenant.registered`, `tenant.activated`, `tenant.logo-changed`, `member.invited`, `member.role-changed`, `member.removed` |
   | bil | `subscription.started`, `subscription.plan-changed`, `subscription.suspended`, `subscription.reactivated`, `plan-change.requested`, `plan-change.withdrawn`, `plan-change.approved`, `plan-change.declined` |
   | stf | `staff.operator-added`, `staff.enabled`, `staff.disabled`, `staff.signed-out-everywhere` |
   | cnscmd | `batch.submitted`, `batch.finished` |

   An action's ledger type is its name, so the ledger tells actions (with a dot) from consent lifecycle events
   (without). The hash chain is unchanged: every entry, of either kind, is chained and checkpointed.
5. **People read it.** `GET /api/aud/activity` returns entries newest first, filtered to everything, actions only or
   consent only, with paging. Each entry carries who acted and its details.
   - **Platform staff** see every tenant, or one. Administrators can also verify the whole chain.
   - **A fiduciary's** Owners, Administrators, Auditors and API clients see their own organisation. Consent managers
     and developers don't.
   - **Staff names are hidden from fiduciaries:** staff appear to them as "Platform staff".
   - **Consent events show only the purpose and the party** (the fiduciary or the person), never contact details.
   - **Screens:** CM Admin gets an **Audit** page (all tenants, a tenant filter, chain verification) and an Audit tab on
     each tenant. The DF Console gets an **Audit** page.

## Consequences

- "Who changed this, when" is answerable from the product, and the answer sits in a chain that can't be quietly
  edited.
- **More ledger entries:** a 10,000-row batch adds two actions (submitted, finished), not 10,000. Each row's consent
  request is already recorded by the consent lifecycle.
- **Consent lifecycle events still don't name the individual who acted,** only the party: a fiduciary's request doesn't
  say which member or system raised it. The batch actions do. Putting an actor on those events is a later change to
  their contract.
- **Staff account changes can, in an outage, be missing from the ledger** while present in Keycloak. The service logs
  them in full for replay, and Keycloak's own admin events are the backstop.
- **A new `stf` Pulsar namespace** in both tenants (cm, cm-sbx).

## Compliance impact

- Accountability for administrative actions (DPDPA s.8, GDPR Art. 5(2) and 30, SOC 2 CC6/CC7, ISO 27001 A.8.15).
- Data minimisation in the audit trail itself: no principal contact data in actions. Payloads remain erasable as
  ADR-014 provides, leaving the chain verifiable.
