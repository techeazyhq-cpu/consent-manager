# ADR-057: A consent check that can't be made is a 503, not a deny

- **Status:** Accepted
- **Date:** 2026-11-07
- **Amends:** [ADR-016](adr-016-circuit-breakers-on-every-synchronous-call.md) (the `pdp` row of its table),
  [ADR-006](adr-006-consent-token-and-pep-pdp.md) (decision 2: "anything else is a 403")
- **Relates to:** [ADR-027](adr-027-short-lived-tokens-status-list-and-verification-availability.md) (fail-closed by
  default), [ADR-028](adr-028-accessible-consent-without-deceptive-design.md) (no asking again for consent already given),
  VAPT finding V-17 ([vapt-and-performance.md](../security/vapt-and-performance.md))

## Context

The gateway's PEP (`PepVerifyGatewayFilter`) asks OPA whether a fiduciary may use a consent token. ADR-016 made the
`pdp` breaker fail closed as a *decision*: a timeout, an error or an open breaker fell back to `false`, and the caller
got the same 403 `consent-not-verified` as for a withdrawn consent. OPA did the same inside the policy: when its
`http.send` to cm-cns-cmd-svc's JWKS or live-status endpoint timed out, the rule was undefined and `allow` was false.

The load rerun of 7 November 2026 showed the cost. Past about 135 requests a second, PostgreSQL saturated, OPA's
live-status lookups timed out, the `pdp` breaker opened, and 13,043 checks of consents that were given and not
withdrawn came back 403. A fiduciary can't tell "no consent" from "try again". One that acts on the answer stops
processing, or asks the person again for consent they gave, which ADR-028 rules out; in an outage that reaches every
principal at once.

## Decision

1. **Three outcomes, not two.** The PEP's answer is one of:
   - **allow**: forwarded to cm-cns-qry-svc, as before;
   - **deny**, 403 `urn:cm:problem:consent-not-verified`: the PDP decided no. No token, a token that doesn't verify,
     another fiduciary's, another artefact's, another purpose, `DENY`, expired, or the artefact not `ISSUED` (revoked,
     lapsed, unknown);
   - **undetermined**, 503 `urn:cm:problem:consent-check-unavailable` with `Retry-After`: the check couldn't be made.
2. **Still fails closed.** An undetermined check is never forwarded. Only the code the caller sees changes.
3. **OPA says when it couldn't decide.** `cns_authz.rego` calls `http.send` with `raise_error: false` and publishes
   `decision := {"allow": ..., "undetermined": ...}`, which the PEP queries instead of `allow`. `undetermined` is true
   when
   - the signing keys can't be had (any answer but 200 from the JWKS), for a well-formed request (`READ`, with a
     caller); or
   - the token grants the request but the live status can't be had: unreachable, timed out, 5xx or 429, as
     `RemoteFailures` counts it (ADR-016 decision 3). A 404 is an answer: no such artefact, a deny.

   A definite deny wins: a token for another fiduciary or purpose is denied even while the status is unreachable.
4. **The PEP's own failures are undetermined too:** OPA unreachable, slower than the `pdp` time limit (2 s), an error,
   the breaker open, or an answer with no decision (no policy loaded).
5. **`Retry-After`:** the breaker's open-state wait (30 s) when the breaker is open, since OPA won't be asked before
   then; 1 s otherwise.
6. **ADR-016's table** now reads, for cm-gw `pdp`: "503 `consent-check-unavailable` with `Retry-After`; never an allow
   (fails closed, ADR-057)".

## Consequences

- Under overload or an outage, consent checks fail as 503 like every other call, and fiduciaries can retry instead of
  treating the consent as withdrawn.
- A client that treated any non-200 as "no consent" still fails safe; one that reads the status code can now tell the
  two apart. The public API (`df-api.openapi.yaml`) documents the 503.
- A deny depends only on what was checked. A forged or mismatched token is still a 403 while the status endpoint is
  down, so the 503 doesn't help an attacker tell a valid token from a forged one beyond what the signature already
  tells them: an undetermined answer about the status is given only to a token that verifies and grants.
- OPA's lookups (2 s) and the gateway's `pdp` time limit (2 s) are the same, so under load the gateway usually gives up
  first; either way the answer is 503. Tuning them apart is left until the next load run shows a need.
