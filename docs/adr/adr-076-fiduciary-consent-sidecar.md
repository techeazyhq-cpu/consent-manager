# ADR-076: A consent sidecar a fiduciary runs next to its services

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #91

## Context

The gateway enforces consent for requests that come through it (ADR-006, ADR-013, ADR-057): a policy engine verifies the consent token
and asks for the consent's live status. A fiduciary's own services, which hold the personal data, make that decision themselves
when they use it, and the only way they have to ask is to sign requests to the Consent Manager's API on every use. That puts the
Consent Manager on the fiduciary's data path with no help for the cases that matter: a burst of checks, a short outage, a
misconfigured key. Issue #91 asked for a fiduciary-hosted enforcement point and "fully offline" validation of consent tokens.
Full offline validation needs the revocation status list that ADR-027 proposes and the platform does not yet publish: without it,
an offline check cannot see a withdrawal, which is the one thing it exists to honour.

## Decision

1. **A sidecar the fiduciary runs** (`sdk/pep-sidecar`, zero dependencies, a Dockerfile): one local call, `POST /v1/check
   {cnsArtId, purpose}`, answered `ALLOW`, `DENY` or `UNAVAILABLE`. It uses the Consent Manager's public API with the fiduciary's own
   signing key; nothing new is added to the platform. It listens on localhost by default.
2. **Three answers, never two** (as in ADR-057 and ADR-069). `UNAVAILABLE` is HTTP 503 with `Retry-After`; `DENY` is an answer
   (200). A fault in the sidecar itself, such as a refused key, is `UNAVAILABLE` with reason `sidecar-fault`, logged, and never a yes.
3. **A live answer is reused for a few seconds** (default 5) and no longer: the sidecar is at most that late to a withdrawal.
4. **Grace mode, off by default.** With `PEP_GRACE_SECONDS`, if the Consent Manager cannot be reached the sidecar may keep
   answering ALLOW for a consent it saw allowed live within that time, **only** while the consent token it holds is unexpired and
   its RS256 signature verifies, offline, against the platform's published keys, for that consent, purpose and fiduciary.
   The answer carries `basis: offline-token` and `staleUntil`. A DENY ends grace for the consent; `PEP_NO_GRACE_PURPOSES` excludes
   purposes (health, children's data: ADR-027 says fail closed). Tokens live about 15 minutes, which is the ceiling on grace.
5. **The staleness is stated, not hidden.** A withdrawal during a bridged outage is not seen until the grace ends; the README and
   this ADR say so, and the fiduciary chooses the bound.
6. **Tested** by 12 unit tests (token forgery, expiry and mismatches; cache; grace and every limit on it; the keys cache; the HTTP
   contract; the refused-key fault) and an end-to-end script against the local stack: a real consent token verified offline against
   the published keys, an outage with grace off and on, withdrawal then DENY, and an outage after it not bridged.

## Not yet / limits

- **No status list, so no fully offline validation.** Until the platform publishes the W3C Bitstring Status List of ADR-027
  (a signed list per fiduciary, a `status` claim in the token), an offline verifier cannot see a withdrawal at all. The sidecar
  therefore bridges outages only briefly and never answers without having recently asked the Consent Manager. When the list exists,
  the sidecar can refresh it and replace grace with a list check.
- **An OPA sidecar with the platform's own policy** was the other reading of the issue. The policy is built to call the platform's
  internal status endpoint, which a fiduciary cannot reach, and OPA cannot sign requests with an API key; a Rego bundle would need
  the status list first.
- **No authentication of callers** to the sidecar; it is for the same host or pod.
- **State is in memory.** A restart forgets what it saw allowed, so grace starts empty (the safe direction).
- **Only Node.** The protocol is two HTTP calls and one signature check; a Go or Java build would be the same.

## Options considered

- **Offline signature check alone:** a token lives up to its expiry whatever happened to the consent; that is the gap ADR-027 names.
- **Caching ALLOW for a long time:** the same gap with more steps; the cache is seconds, grace is opt-in and bounded by the token.
- **Fail open when the Consent Manager is down:** breaks withdrawal; ADR-057 already decided the check is 503, never a guess.
