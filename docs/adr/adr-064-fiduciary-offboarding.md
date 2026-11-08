# ADR-064: A fiduciary leaves: offboarding and retention

- **Status:** Accepted on 2026-11-08 with the defaults below (D1-D7 as proposed). Built in pieces; see "Built so far".
- **Date:** 2026-11-07
- **Issue:** #113
- **Builds on:** [ADR-029](adr-029-fiduciary-assurance-and-consent-termination.md) §3-4 (suspension, termination by
  the controller), [ADR-059](adr-059-closing-an-account-erasure-saga.md) (the erasure saga),
  [ADR-014](adr-014-tamper-evident-audit-ledger.md) (the audit ledger), [ADR-063](adr-063-df-terms-and-dpa-acceptance.md).

> This is a design input, not legal advice. Every period below is a default held in configuration, and needs
> confirming with counsel for each cell before launch.

## Context

A fiduciary can be registered and activated, but not leave. `DfSts` is `PENDING` or `ACTIVE`; nothing ends a
relationship, and nothing says what happens to the fiduciary's consents, API keys, team, records and billing when it
does. Two things pull against each other:

- **Principals' records must outlive the fiduciary.** A principal is entitled to their consent records and receipts for
  years (DPDPA First Schedule B4 asks for at least 7 years), whether or not the fiduciary still uses the platform.
- **The platform should not hold a departed customer's data longer than it needs to**, and the fiduciary's own people
  (team, contacts) are personal data too.

## Decision

### 1. Four states, one direction

`ACTIVE → SUSPENDED ↔ ACTIVE` (reversible), and `ACTIVE or SUSPENDED → LEAVING → CLOSED` (not reversible).

| State | Who can do what | How it starts |
|---|---|---|
| `SUSPENDED` | No new consent requests, no new tokens or API calls. Existing consents stay recorded and verifiable. The team can sign in to the console, read and export. | Staff, with a reason (fraud, unpaid terms, a regulator's direction), audited. Resumed by staff. |
| `LEAVING` | As suspended, plus **no new keys** and **no purposes can be published**. A 30-day **wind-down** runs. | The fiduciary's Owner asks to leave (typing the organisation's name), or staff end the contract with a reason. |
| `CLOSED` | Nothing, except staff reading what is still kept, and principals reading their own records. | The wind-down ends. |

### 2. Wind-down: 30 days, then every consent ends

- On entering `LEAVING`, the Owner and the team are told what will happen and when. Principals are not told yet.
- Within the 30 days the fiduciary can **export everything of its own** (consent records, receipts, audit entries about
  it) and **ask to cancel the departure**, which returns it to `ACTIVE` once.
- At the end, **every consent still in force is terminated by the controller** (ADR-029 §4): a terminated record with
  the reason "fiduciary left the platform", a receipt to each principal, a webhook to the fiduciary if its endpoint
  still exists, and the consent stops verifying. Requests still waiting are declined, with the same notice. Each
  principal is told, in their own words, who has gone and that their records are still theirs to read.

### 3. What is erased, and when

At closing, and run as a saga like ADR-059 (a step per service, idempotent, retried, `STUCK` after 8 tries and visible
to staff):

| Data | Treatment | When |
|---|---|---|
| API keys, signing secrets, OAuth clients, webhooks | Revoked and deleted | At `LEAVING` (stop issuing), deleted at `CLOSED` |
| Team members and their Keycloak accounts | Deleted | `CLOSED` |
| Organisation profile, logo, contact details | Deleted, except the legal name, registration numbers and the closing date | `CLOSED` |
| Sandbox data | Deleted | `LEAVING` |
| Usage counters, invoices | Kept, as accounting records | **8 years** from the last invoice (tax law varies by cell; confirm) |
| Purposes, notices and their versions | Kept (they are what principals agreed to) | With the consent records |
| **Consent records, receipts, history of the fiduciary's principals** | **Kept, with the principal identified by pseudonymous id only** | **7 years after the last event on the consent**, then deleted |
| Audit ledger entries | Kept (the hash chain must still verify); payloads about the fiduciary's team erased as in ADR-059 | Same 7 years |
| Acceptances of terms and DPA (ADR-063) | Kept: it is the evidence of the agreement | 7 years after `CLOSED` |
| Contacts the fiduciary used to reach principals (email or phone in request rows and events) | Erased, as ADR-059 does for a principal | `CLOSED`, as the fiduciary's contact lists are the fiduciary's data |

The consequence to be accepted: **after `CLOSED`, a principal can still see their record of consent given to a fiduciary
that no longer exists**, and the record still verifies; but nobody can contact the principal on the fiduciary's behalf.

### 4. Purge

A daily job deletes consent records and ledger payloads whose retention has passed, for closed fiduciaries only. A
legal hold (ADR-021 §4) set on a fiduciary or a principal stops it for those records. Each purge is itself an audit
entry (counts only).

### 5. Re-registering

A closed fiduciary cannot be reopened. The same organisation can register again as a **new** fiduciary with a new
id; its old records are not reattached. The registration numbers are kept, so staff can see a returning organisation.

### 6. Who decides

- Owner-initiated leaving: the Owner alone, confirmed by typing the organisation's name; staff are told.
- Staff-initiated: needs a second staff member's approval (ADR-029 §5's two-person rule), with a reason.
- Cancelling the wind-down: the Owner, or staff with the same two-person approval.

### 7. Pieces, in the order they would be built

1. States and suspension: `SUSPENDED` in cm-df-reg-svc, and the gateway and token checks that already read "is active".
2. Leaving and the wind-down timer, export, cancel.
3. The saga: terminate consents (cm-cns-cmd-svc), delete the team (Keycloak), keys and webhooks, contacts.
4. The retention purge job and legal holds.
5. DF Console and CM Admin screens; runbook; CI check that every retention period is configuration.

## Built so far

1. **States and suspension** (done): `SUSPENDED` in cm-df-reg-svc. Staff suspend with a reason (an operator or an
   administrator) and an administrator resumes: `POST /api/df-reg/dfs/{id}/suspend` and `/resume`. A suspended
   fiduciary fails the "is it active" checks that already existed (API keys are refused with `df-suspended`; no request
   can be raised), so nothing else had to change. Its team can still sign in and read. Keys, team and consents are
   untouched, so resuming restores it exactly. Both are audited.
   *Known limit:* an OAuth token a fiduciary's system already holds works until it expires (minutes).
2. **Leaving and the wind-down** (done): `LEAVING`. The Owner asks with `POST /api/df-reg/me/leave` (typing the
   organisation's name), sees it with `GET`, and calls it off once with `POST /api/df-reg/me/leave/cancel` (back to
   ACTIVE, or to SUSPENDED if that is where it left from). The wind-down is `cm.dfreg.offboarding.wind-down-days` (30).
   The whole team is emailed at each step; a mail failure doesn't stop the change. While leaving, API keys are refused
   with `df-leaving` and no new key can be issued (also true when suspended).
   *Not in this piece:* the **export** (records already come from the consent and audit APIs, but one "everything of
   mine" export is a cross-service job), **staff ending a contract** (needs the two-person approval of D7), and
   **anything happening when the wind-down ends**: the date is recorded, and piece 3 acts on it.
3. **Closing** (done, in part): a job in cm-df-reg-svc (every minute, `cm.dfreg.closing.fixed-delay-ms`) finds each
   `LEAVING` fiduciary whose wind-down has ended and closes it in two parts. *Clean-up* (each step can be repeated):
   cm-cns-cmd-svc ends every consent in force and declines every waiting request through the domain, in batches
   (`POST /internal/dfs/{id}/close-consents`, so principals get their receipts and the fiduciary's webhooks fire), then
   erases the contacts the fiduciary sent requests to; every API key is revoked; the team's Keycloak accounts are
   removed; the logo is deleted. *Finish* (one transaction): `CLOSED`, which is final; the API client secret is dropped,
   the contact address and profile are erased except the display and legal names and the registration numbers, and
   `tenant.closed` is audited with the counts. A failure at any step is logged as `[CM-DF-CLOSING-FAILED]`, leaves it
   `LEAVING`, and the next pass starts again.
   *Kept, as decided:* consent records under their pseudonymous ids, purposes, the audit ledger, invoices, the acceptances
   of the terms.
   *Differs from the text above:* ended consents are recorded as **revoked** (the only ending the Consent context has),
   not as the distinct ISO/IEC TS 27560 "terminated by controller" state with the reason "fiduciary left the platform";
   the reason is in the audit entry. Principals receive the revocation receipt, not a custom notice naming who has gone.
   *Not yet:* the Keycloak API client, webhooks endpoints and the ledger's payloads about the team are not removed;
   the sandbox copy is not touched.
4. **Retention purge** (done, in part): an hourly job in cm-df-reg-svc (`cm.dfreg.retention.*`) takes each `CLOSED`
   fiduciary that is not under a legal hold and is older than `cm.dfreg.retention.consent-days` (2555, seven years) and
   has each holding service delete what is past that: cm-cns-cmd-svc its consent requests with their consents, records,
   events and receipts (and, when none is left, its purposes, webhook endpoints and batches); cm-cns-qry-svc its read-model
   rows; cm-aud-svc the payloads and principal links of its ledger entries; and cm-df-reg-svc its acceptances of the terms.
   When nothing is left it is marked purged (`tenant.purged`) and not looked at again. Calls are batched and repeatable.
   *The append-only guarantees:* the consent events, receipts and published purpose versions are protected by triggers that
   refuse any change. The purge is the one deletion allowed: it sets `cm.retention_purge` for its own transaction
   (migrations V8 and V9 in cm-cns-cmd-svc), and every other delete or update is still refused. The audit ledger's entries
   are never deleted, so the hash chain and checkpoints still verify; only their payloads go, as for an erased principal.
   *Legal hold:* an administrator places one on a fiduciary (`PUT /api/df-reg/dfs/{id}/legal-hold` with a reason, `DELETE`
   to release); nothing of it is purged while held. Both are audited.
   *Not yet:* a hold on a **principal** (only whole fiduciaries can be held); the **8-year invoice** retention in
   cm-bil-svc (D3) and its usage counters; the Redis cache entries of purged consents (they expire); the sandbox copy.
   The retention period is one setting for all records of all cells: a cell with a different statutory period needs it
   made per cell. 7 years is a default to be confirmed with counsel, as the ADR says above.
5. **Screens and runbook** (done): the DF Console has an *Agreements* page (each document's version and acceptance, and
   for the Owner Accept; leaving by typing the organisation's name, and cancelling once) and a banner on every page while
   the organisation is suspended, leaving or closed. CM Admin's tenant page has a *Standing* card (suspend with a reason,
   resume, place or release a legal hold) and the dashboard filters by the new standings. The
   [offboarding runbook](../runbooks/offboarding.md) covers each state, the two failure log codes and the checks.
6. **Staff ending a contract** (done): a member of staff (operator or administrator) proposes with a reason
   (`POST /api/df-reg/dfs/{id}/end-contract`); a *different* administrator approves or rejects it
   (`POST /api/df-reg/leave-proposals/{id}/approve|reject`), and only then does the wind-down start. The proposer can
   withdraw. One proposal waits per fiduciary (a unique index), and the database refuses a decision by the proposer. Calling a
   departure off by staff (`.../cancel-end-contract`) takes the same two people, and does not depend on or use the Owner's
   one cancellation. The team is emailed with the reason, and every step is audited with who proposed and who approved. In CM
   Admin it is the *Ending the contract* card on the tenant page.
7. **Export** (done, with a limit): `GET /api/df-reg/me/export`, for an Owner or Administrator while the organisation is
   active, suspended or leaving, returns one JSON file (`cm:FiduciaryExport`): the profile and standing, the team, the API
   keys by prefix only (no key, signing secret, hash or client secret is ever in it), acceptances of the terms, the purposes
   with every version, the consent records as ISO/IEC TS 27560 documents with their signed receipts, and the audit
   entries with their payloads. df-reg assembles it from cm-cns-cmd-svc and cm-aud-svc (`GET /internal/dfs/{id}/export`).
   Taking one is audited (`tenant.exported`). It is refused once the organisation has closed (`export-unavailable`).
   *The limit:* the consent records and audit entries are each capped at 5,000, and the file says `truncated: true` when it
   was cut; a larger organisation has to ask the platform for the rest. Building it takes about thirty seconds at the cap
   (the receipts and events are read per record), so it has its own gateway route, `df-reg-export`, with its own circuit
   breaker and a 120 s limit, rather than the 10 s every other call has. The honest fix for large organisations is an
   asynchronous export that is built in the background and downloaded when ready; it is not built.

## What the owner needs to decide

| # | Default | Alternatives |
|---|---|---|
| D1 | Wind-down is **30 days** | 14 (faster exit), 90 (slower, kinder to large fiduciaries) |
| D2 | Consent records kept **7 years after the last event** | 6 (GDPR-style), 10 |
| D3 | Invoices kept **8 years** | follow each cell's tax law, set per cell |
| D4 | Principals are told **at the end** of the wind-down | at the start, so they can withdraw first |
| D5 | A departure can be **cancelled once** | never, or any time before `CLOSED` |
| D6 | A closed organisation re-registers as a **new** fiduciary | allow reopening within 90 days |
| D7 | Staff-initiated closing needs **two staff** | one, with an audit entry |

## Consequences

- Leaving becomes a defined, audited, recoverable-until-the-end process, rather than a database operation by hand.
- The platform keeps principals' records for years for fiduciaries it no longer serves. That is deliberate, and stated to
  principals in their notice and in the platform's own privacy notice.
- Retention periods become configuration with a test, so changing one is a reviewed change rather than a hunt through code.

## Options considered

- **Delete everything at once on leaving:** simplest, and destroys the principals' own evidence, which is not ours to destroy.
- **Keep everything forever:** no engineering, and no answer to "how long do you hold my data?"
- **Hand the records to the fiduciary on leaving:** puts the principal's evidence with the party it is evidence
  against, and the consent manager exists to hold it independently.
