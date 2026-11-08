# Runbook: a fiduciary leaves (suspension, leaving, closing, retention)

For platform staff and on-call engineers. Design and the reasons for each choice are in
[ADR-064](../adr/adr-064-fiduciary-offboarding.md). The error and log codes are in the
[error dictionary](../errors/error-dictionary.md).

## The states

| State | Meaning | Who moves it |
|---|---|---|
| `ACTIVE` | Normal. | |
| `SUSPENDED` | Stopped by staff, reversibly. No new requests, API keys refused (`df-suspended`). The team can sign in and read. | Staff: suspend (operator or administrator), resume (administrator). |
| `LEAVING` | The Owner asked to leave. A 30-day wind-down (`cm.dfreg.offboarding.wind-down-days`). API keys refused (`df-leaving`), no new keys. The Owner can cancel once. | The Owner, in the DF Console under Agreements. |
| `CLOSED` | Final. Consents ended, keys revoked, team and logo removed, profile erased except names and registration numbers. Records kept for the retention period. | A job, when the wind-down ends. |

## Suspending a tenant

CM Admin, open the tenant, **Standing**: type the reason (it is shown to the tenant's team and kept in the audit trail)
and press **Suspend**. **Resume** puts it back exactly as it was; its keys, team and consents were never touched.
Over the API: `POST /api/df-reg/dfs/{id}/suspend` with `{"reason": "…"}` and `POST …/resume`.

A token a tenant's system already holds keeps working until it expires (minutes). Revoke its API keys as well if that
matters.

## A tenant has asked to leave

Nothing for staff to do. It appears as **Leaving** on the dashboard with its closing date.

## Ending a tenant's contract (staff)

It takes two people. In CM Admin, open the tenant, **Ending the contract**: an operator or administrator types the reason
and proposes. A **different administrator** then approves (the 30-day wind-down starts and the team is emailed the
reason) or rejects it. The proposer can withdraw it, and cannot decide it. Only one proposal waits per tenant. To call a
departure off (whoever began it) use the same card: it needs the same two people, and does not use up the Owner's one
cancellation. To stop a tenant **now**, suspend it: that needs one person and is reversible.

If an Owner asks you to cancel a departure they already cancelled once, it cannot be cancelled again; that is the
design (D5). Changing it is a decision for the owner of the platform, not a database edit.

## Closing did not happen

The closing job runs every minute (`cm.dfreg.closing.fixed-delay-ms`). A tenant past its closing date that is still
`LEAVING` failed a step. The log line is:

    [CM-DF-CLOSING-FAILED] Closing fiduciary <id> failed; it stays LEAVING and is tried again

with the cause below it. The usual causes and fixes:

| Cause | Fix |
|---|---|
| `cm-cns-cmd-svc` unreachable or failing | Fix it; the next pass retries. Its endpoint is `POST /internal/dfs/{id}/close-consents` and it can be repeated. |
| Keycloak unreachable | Fix it; removing the team can be repeated. |
| A step times out on a large tenant | It works in batches of 200 consents; run again. |

Every step can be repeated, so retrying is safe. You do not need to repair anything by hand.

## Retention and the purge

A `CLOSED` tenant's records are deleted by an hourly job (`cm.dfreg.retention.*`) once they are older than
`cm.dfreg.retention.consent-days` (2555, seven years). This is the **only** deletion of consent events, receipts and
published purposes the platform allows: they are protected by database triggers, and the purge says so for its own
transaction with `cm.retention_purge`. If a purge fails you will see:

    [CM-DF-PURGE-FAILED] Purging fiduciary <id> failed; it is tried again on the next pass

The calls are `POST /internal/dfs/{id}/purge` on `cm-cns-cmd-svc`, `cm-cns-qry-svc` and `cm-aud-svc`. Nothing partly
deleted is lost: they can be repeated.

**The retention period is a default to confirm with counsel for each cell** before launch. Changing it is a
configuration change on `cm-df-reg-svc`, with a review.

### Legal hold

If a regulator, a court or an investigation needs a tenant's records kept, place a hold **before** the retention ends:
CM Admin, the tenant, **Standing**, **Place a legal hold**, with a case or order reference. Nothing of that tenant is
purged while it is held, and releasing it lets the next hourly pass continue. Both are in the audit trail. A hold covers a
whole tenant; holding a single principal's records is not built.

## Checks

- Is the ledger still intact? CM Admin, Audit, **Verify**, or `GET /api/aud/chain/verify`. After a purge it reports how
  many payloads were erased; entries are never deleted, so the chain stays intact.
- Did the purge finish for a tenant? The tenant list has `purgedAt`; the audit trail has `tenant.purged` with counts.
- What a tenant's closing deleted: the `tenant.closed` audit entry has the counts of consents ended, requests declined,
  contacts erased, keys revoked and team accounts removed.

## Not built yet (so do it by hand, carefully, or wait)

- One export of everything a leaving tenant has (today: its consent records, receipts and audit entries come from their
  own pages and APIs).
- Removal of the tenant's Keycloak API client and webhook endpoints at closing, and its sandbox copy.
- The 8-year retention of invoices and usage in billing.
