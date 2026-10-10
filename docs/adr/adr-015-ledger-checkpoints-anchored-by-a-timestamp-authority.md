# ADR-015: Ledger checkpoints, anchored by an external RFC 3161 timestamp authority

- **Status:** Accepted
- **Date:** 2026-10-10

## Context

The hash chain of ADR-014 makes any change to recorded history visible, except one: an operator with full database
rights can switch off the append-only triggers, change an entry, and recompute every later hash and the chain head.
The rewritten chain is internally consistent and verifies. To catch that, someone outside the operator's control must
have committed, at the time, to what the ledger looked like. The owner chose public anchoring over running a
blockchain network.

## Decision

1. **Checkpoints.** Every `cm.aud.anchoring.interval-ms` (an hour by default; once a minute after start), the entries
   recorded since the previous checkpoint are summarised in a new one: the RFC 6962 Merkle root of their entry
   hashes, the chain head it ends at, and the previous checkpoint's hash, so checkpoints form a chain of their own
   (`AudCheckpoint`, format `cm-aud-checkpoint-v1`; `AudMerkle`). Nothing personal is in a checkpoint.
2. **Anchoring with RFC 3161.** Each checkpoint's hash is sent, alone, to a public timestamp authority
   (`cm.aud.anchoring.tsa-url`, DigiCert's `http://timestamp.digicert.com` by default), which returns a token signed
   with its certificate: proof by a third party that this hash existed no later than the time it states. The client
   (`Rfc3161TimestampAuthority`, Bouncy Castle) sends a fresh nonce and asks for the certificate, accepts only a
   granted answer for exactly this hash and nonce, and checks the token's signature before storing it, as returned, in
   `aud.checkpoint_anchor`. An authority that is down only delays anchoring: the checkpoint waits and is anchored on
   a later run.
3. **No signature of our own.** An operator's signature on a checkpoint proves nothing against that operator, who holds
   the key; the timestamp authority's signature does. A second, independent authority can be added the same way.
4. **Append-only.** Triggers refuse `UPDATE`, `DELETE` and `TRUNCATE` on `aud.checkpoint` and
   `aud.checkpoint_anchor`.
5. **Verification, by us and by anyone.**
   - `GET /api/aud/chain/verify` (administrators) now reports two verdicts: the chain (ADR-014), and the checkpoints:
     contiguous ranges, each hash and link, each Merkle root and head recomputed from the entries, and each token's
     signature and that it was issued for exactly the checkpoint's hash.
   - `GET /api/aud/entries/{seq}/proof` gives whoever the entry concerns (an administrator, its fiduciary, its
     principal) the entry, its Merkle path, the checkpoint and the token: everything needed to prove offline, with
     standard tools, that the entry is part of the ledger as the authority timestamped it.
   - `GET /api/aud/checkpoints` lists the checkpoints and tokens for anyone signed in.
   - [docs/audit-ledger.md](../audit-ledger.md) shows how to check a proof with `openssl` and a few lines of code.

## Consequences

- Rewriting history now has to contradict a third party's signature: a rewritten ledger produces different Merkle
  roots, and a checkpoint recomputed to fit it no longer matches its token. A test does exactly this rewrite and the
  checkpoint verification catches it.
- Only entries up to the last anchored checkpoint are protected this way; the latest hour (one interval) relies on the
  chain alone until the next checkpoint.
- The service makes one outbound request per checkpoint to the authority, carrying a hash and nothing else. Plain HTTP
  is the norm for RFC 3161: the token is signed, so the transport adds nothing to its integrity.
  Where it cannot reach one, anchoring is turned off (`CM_AUD_ANCHORING_ENABLED=false`) or pointed at an internal
  authority, and checkpoints still chain.
- The token proves the authority's statement; trusting it means trusting that authority's CA, which is what
  `openssl ts -verify -CAfile` checks. Choosing the authority (or several) is an operator decision per deployment.
