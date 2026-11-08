# ADR-077: A signed status list for consent tokens

- **Status:** Accepted; implements decision 4 of [ADR-027](adr-027-short-lived-tokens-status-list-and-verification-availability.md),
  with a different format (below)
- **Date:** 2026-11-08
- **Issue:** #91

## Context

A consent token (ADR-006, ADR-013) is signed and short-lived (15 minutes, ADR-027). A fiduciary that checks only the signature
cannot see a withdrawal that happened after the token was issued, so it either asks the Consent Manager about every use (the live
check, which puts the platform on every data path) or honours a withdrawn consent for up to a token's life. ADR-027 proposed a
revocation status list for this. It was never built, which is what stopped the consent sidecar (ADR-076) from being able to run
without calling the platform.

## Decision

1. **Every consent token names its place in a status list.** A `status` claim carries `{status_list: {idx, uri}}`: the address of
   the issuing fiduciary's list and the token's index in it. The index is chosen at random, once per consent, from a list of 262,144
   places and kept (`cns_cmd.status_slot`), so the same consent has the same index in every token and the index says nothing about
   whose consent it is. Tokens issued before this change carry no `status` claim and keep working with the live check.
2. **The list is the IETF Token Status List** (a JWT of type `statuslist+jwt`, one bit per index, zlib-compressed, base64url),
   **not the W3C Bitstring Status List** ADR-027 named. Both are a signed bit string with a per-token index; the IETF form is a
   plain JWT, which this platform already issues and verifies with the same keys and libraries, where the W3C form is a Verifiable
   Credential envelope that would need a second data model for no other use. The privacy properties ADR-027 wanted (random
   index, one list per fiduciary, no per-token query) are the same.
3. **Signed with the token keys,** so a verifier that holds the published key set (`/.well-known/jwks.json`) needs nothing else.
   Its `ttl` is 60 seconds: a verifier may use a list for that long, and a list past its `exp` is not an answer.
4. **Open to anyone.** `GET /api/cns-cmd/status-lists/{dfId}` needs no credentials, since the list holds bits and no names. It is
   built from the live state of the fiduciary's consents (a bit is set when the consent was withdrawn, lapsed or has expired), kept
   for 10 seconds, and served with `Cache-Control: max-age=30`, so a burst of requests costs one query. An unknown fiduciary gets an
   all-zero list, which says nothing; only a fiduciary that has issued tokens is cached, so the endpoint cannot be used to fill memory.
5. **The sidecar gains list mode** (`PEP_MODE=list`, ADR-076): it fetches the consent token once, then answers from the token's
   signature and the list's bit, fetching the list again only when the one it holds expires. The platform sees about one request a
   minute per fiduciary instead of one per use, and a withdrawal reaches the fiduciary within a list's `ttl` plus the 10 seconds
   the platform keeps a built list: the bound ADR-027 asked for, now stated by the list itself. The sidecar only fetches a list
   from the origin of the gateway it is configured for, never from an address a token names alone.
6. **Retention:** a fiduciary's slots are removed with its other records when the retention purge runs (ADR-064).

## Not yet / limits

- **The live check stays.** The gateway's policy engine and the default sidecar mode still ask the platform's write side, which
  is current to the second. The list is for verifiers that cannot or should not call the platform on every use; its bound is
  a minute or so, not instant.
- **Slots are not reused.** A fiduciary that has been issued more consents than about half the list's size (130,000) will find
  free places harder to draw, and at 12 failed draws in a row a token cannot be issued. Pruning slots of long-ended consents, or a
  bigger list for a big fiduciary, is the remedy; neither is built. A list of this size is about 33 KB before compression and a few
  hundred bytes while mostly empty.
- **Only the signature of the list protects it.** A verifier that skips the check, or accepts a list it did not fetch from the
  platform, can be shown an all-zero list. The sidecar checks the signature, the type, the subject address and the expiry.
- **No status for the purpose or scope,** only "ended or not": narrowing a consent's categories is a new token, not a bit.
- **Verifiers in other languages** need the same ten lines (inflate, test a bit); only the Node sidecar exists.

## Options considered

- **The W3C Bitstring Status List,** as ADR-027 said: the same idea in a heavier envelope (see decision 2).
- **A deterministic index from the consent's id:** no table, but two consents could share an index, so one withdrawal would
  deny another person's consent.
- **Revoking by token id (a deny list):** grows with the number of withdrawals and tells a verifier exactly which consents ended;
  a bit string gives neither.
- **OCSP-style per-token queries:** the platform learns which consent a fiduciary is using right now (ADR-027's own objection).
