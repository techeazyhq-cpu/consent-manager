# ADR-014: A tamper-evident audit ledger, hash-chained like a blockchain

- **Status:** Accepted (anchoring to an external timestamp authority follows in ADR-015)
- **Date:** 2026-10-10

## Context

A consent manager must be able to prove, to a principal, a fiduciary or the Data Protection Board, what happened to
a consent and when: that a request was raised, that the principal granted or refused it, that it was withdrawn. Logs
and database rows can be edited by whoever operates the platform, so a record that only says "trust us" proves little.
The owner asked for audit records that behave like a blockchain: history that cannot be rewritten without it showing.
A permissioned blockchain network would give that at a large operational cost; a hash chain gives the same property
for one operator, and anchoring its state with an outside authority (ADR-015) removes the need to trust that operator.

At the same time, the DPDPA gives principals the right to have their personal data erased, which an immutable record
of personal data would make impossible.

## Decision

1. **A new bounded context, `cm-aud-svc`**, records every platform event: the consent lifecycle (`CnsReqCreatedEvt`,
   `CnsGrantedEvt`, `CnsDeniedEvt`, `CnsRevokedEvt`, `CnsDpLinkedEvt`, `CnsExpiredEvt`) and fiduciary activation
   (`DfActivatedEvt`). It reads the same Pulsar topics the platform acts on, under its own subscriptions, so it
   records exactly what the platform did (ADR-004); an event is acknowledged only once recorded, and a redelivered
   event is recognised by its `evtId` and recorded once.
2. **A hash chain.** Every entry stores the SHA-256 of its own fields and of the previous entry's hash
   (`AudChain`, format `cm-aud-v1`, the first entry pointing at 64 zeros). Changing, removing, inserting or
   reordering any entry changes every hash after it. Times are kept to the microsecond so that what PostgreSQL stores
   is exactly what was hashed.
3. **Nothing personal in the chain.** An entry holds the event's id, type and times, the ids of the consent request,
   artefact and fiduciary, and a SHA-256 of the payload. The payload itself (which may name the principal, their
   email or phone) is stored in `aud.entry_payload`, and the principal an entry concerns in `aud.entry_subject`: the
   only two tables erasure may delete from. Erasing them leaves the chain intact and verifiable; the entry still
   proves that *something* with that digest was recorded at that point. A consent token in a payload is replaced by
   its SHA-256: enough to prove which token was issued, useless to present.
4. **Append-only in the database.** A trigger refuses every `UPDATE`, `DELETE` and `TRUNCATE` on `aud.entry`. Appends
   take a row lock on `aud.chain_head`, so concurrent consumers can never fork the chain.
5. **Verification anyone can repeat.** `GET /api/aud/chain/verify` (administrators) walks the chain from the first
   entry and reports the first entry whose sequence, link, hash or payload digest does not hold, and how many
   payloads were erased. The rules are in one class and in this record, so an auditor can re-implement them. `GET
   /api/aud/chain/head` gives anyone signed in the current sequence and hash to note and check against later.
6. **Each party sees its own history.** `GET /api/aud/entries` is scoped by the caller's token, like the read model:
   an administrator can look up any request, artefact or fiduciary; a fiduciary sees the entries about itself; a
   principal sees the entries about them.

## Consequences

- Rewriting history is detectable by anyone who verifies: the chain breaks at the first altered entry.
- **The chain alone does not stop the operator.** Someone with full database rights can drop the trigger and rewrite
  the whole chain consistently from the altered entry onwards. Detecting that needs a copy of a past chain head held
  by someone else: ADR-015 summarises the chain in periodic checkpoints and has an external RFC 3161 timestamp
  authority sign each one.
- History starts when the ledger first subscribes: Pulsar does not keep acknowledged messages, so events from before
  its deployment are not in it. A one-off backfill from the services' outboxes can add them, in their original order,
  before the ledger goes live.
- Verification reads the whole chain; that is fine for millions of entries, and checkpoints (ADR-015) let a verifier
  start from a trusted checkpoint instead of the first entry.
- The ledger is one more consumer of every event and one more schema; it never blocks the platform, which does not
  wait for it.
