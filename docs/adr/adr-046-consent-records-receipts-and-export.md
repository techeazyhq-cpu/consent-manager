# ADR-046: Consent records written with each change; receipts signed with their own key; export

- **Status:** Accepted
- **Date:** 2026-10-20
- **Implements:** [ADR-025](adr-025-iso-27560-consent-records-and-receipts.md) §1, §2 and §3 in part; DPDPA First
  Schedule B3 and B4

## Context

ADR-025 decided that every consent has an ISO/IEC TS 27560 record, owned by the write side and extended in the same
transaction as each change. It also decided that the principal gets a receipt signed with a key of its own on every
change, and can download everything. With purpose versions (ADR-043), the record's Notice and Processing sections
have an immutable source. ADR-044 and ADR-045 give it the full set of events.

## Decision

1. **The record is written from the change's own domain event, in its transaction.** Every change of a consent
   already raises a domain event that `ObxWriter` puts in the outbox, inside the change's transaction. The same call
   appends to the record:
   - `CnsRecording` maps each event to a record entry, in the domain.
   - The adapter writes it under a transaction-scoped advisory lock on the record, so two changes never take the
     same place in it.
   - No handler can forget to record, and the record can't disagree with the consent.
2. **Record shape** (`cns_rec`, `cns_rec_evt`):
   - **Header:** record id, request, fiduciary, and the purpose version and notice hash the request named. The
     subject is the principal's pseudonymous id, set when the request is linked; never a contact.
   - **Events:** `REQUESTED`, `GIVEN`, `REFUSED`, `MODIFIED`, `RENEWED`, `WITHDRAWN`, `LAPSED`, each with:
     - its W3C DPV status
     - who acted
     - the categories and validity it left in force
     - for a modification, what changed
   - Events and receipts are append-only: a trigger refuses any update or delete.
3. **The document** (`CnsRecDoc`, JSON-LD with DPV) is rendered from the record and its purpose version. Both are
   immutable or only grow, so a record always renders the same document. It contains Header, Notice (with its text
   when the hash matches), Processing, Parties and Events, plus the current status. Processing covers:
   - purposes, and personal data with DPV types
   - legal basis `dpv:Consent`
   - the controller by name
   - recipients and transfers
   - storage duration and validity bounds
   - jurisdiction, withdrawal method and rights

   The platform's name, jurisdiction and grievance contact come from configuration until regime profiles (ADR-020)
   and cells (ADR-018) supply them.
4. **Receipts** are issued for every record event by `CnsRcptJob`, a scheduled pass every 2 seconds that issues each
   in its own transaction.
   - **What one is:** the record as it stood after that event, signed as a compact JWS (RS256,
     `typ: cm-receipt+json`).
   - **The key:** the receipt key set, which is not the token key set (finding R8). Its kids start `cns-rcpt-`. It
     lives in `receipt-jwks.json`, or the `CNS_RCPT_JWKS` secret in production. Retired keys stay in the set and are
     published at `/api/cns-cmd/.well-known/receipt-jwks.json`.
   - **Trusted time:** the receipt's SHA-256 goes to the audit ledger as `CnsRcptIssuedEvt`, in the same transaction.
     The ledger's RFC 3161-anchored checkpoints (ADR-015) then give it trusted time.
5. **Who reads what:**
   - The principal the record names and the fiduciary that is its controller can read a record
     (`GET /api/cns-cmd/records/{cnsReqId}`) and its receipts. Anyone else gets 404.
   - Only a principal exports, and only their own records (`GET /api/cns-cmd/records/export`). The export is every
     record as its document, with every receipt as signed.
6. **Existing consents get records** in migration V4, with events built from what the write side kept: requested,
   given and lapsed have their times. A refusal or withdrawal made before records began didn't keep its time there.
   It is recorded at the migration's time, marked `BACKFILLED`, and the audit ledger holds the exact time.

## Not yet

- **A receipt's inclusion proof** from the ledger checkpoint. The hash is in the chain; serving the proof with the
  receipt comes with the ledger's proof API.
- **An accessible PDF/UA rendering** of a receipt. The portal shows the history accessibly and downloads the signed
  JSON.
- **Retention, legal hold and the WORM archive** (ADR-025 §3).
- **The expiry sweep isn't one transaction per lapse.** Its status update, outbox row and record event are separate
  statements, as the outbox already was.

## Consequences

- Every consent's history is provable: by the principal from their receipts alone, offline, with the published
  receipt keys; and by anyone with the ledger.
- The record names its controller by its registered name, fetched from df-reg when a document is rendered. If
  df-reg is down, the id stands in.
- Each consent event adds one receipt and one ledger entry. Migration V4's back-fill issues receipts for every
  existing event in the background, 100 every 2 seconds.
