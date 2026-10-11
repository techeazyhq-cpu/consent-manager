# ADR-027: Short-lived consent tokens, a revocation status list, and verification availability

- **Status:** Proposed; supersedes the token lifetime of [ADR-006](adr-006-consent-token-and-pep-pdp.md) and the offline
  verification of [ADR-013](adr-013-verified-consent-tokens.md)
- **Date:** 2026-10-11
- **Design:** [compliance architecture §6.3, Appendix B findings R1, R2](../compliance/compliance-architecture.md)

## Context

Two things defeat withdrawal today:

- **Offline verification.** A token's `exp` is the artefact's validity (365 days by default), and the JWKS is
  published so fiduciaries can verify tokens themselves (ADR-013). A fiduciary that checks only the signature keeps
  honouring a withdrawn consent for up to a year.
- **A stale live check.** The gateway's check reads `cm-cns-qry-svc`'s projection, so it lags a revoke by about a
  second.

Withdrawal must be effective as soon as it is made: DPDPA s.6(4) and (6), GDPR Art. 7(3), HIPAA §164.508(b)(5), and
MeitY's consent-management BRD (instant propagation).

At the same time, verification sits on every fiduciary's data path. If it is unavailable, fail-closed stops their
processing, and fail-open breaks withdrawal. That choice was never made explicitly.

## Decision

1. **Token lifetime is separate from consent validity.** A token's `exp` is the earlier of the consent's validity end
   (ADR-026) and the issue time plus the token lifetime. The lifetime comes from the regime profile (ADR-020), and the
   strictest applicable regime wins. Proposed starting values: 15 minutes, or 5 minutes for `PHI` and `CHILD`
   purposes. These are estimates for the DPO and fiduciaries to validate against their need.
2. **Fiduciaries refresh tokens.** `POST /api/cns-cmd/cns-arts/{id}/token`, called with the fiduciary's own client
   credentials, returns a fresh token, but only while the artefact is valid and only to the fiduciary it names.
3. **The write side is the authority on status.** A revoke, a shortened validity or a lapse is written to a revocation
   set in the same transaction as the state change. The gateway's live check (OPA) reads that set through
   `cm-cns-cmd-svc`'s internal status endpoint, not the projection. This closes the ~1 s window.
4. **A revocation status list for offline checks.**
   - **Format:** each cell publishes a **W3C Bitstring Status List** per fiduciary, signed with the token key, served
     with `Cache-Control: max-age` no longer than the token lifetime.
   - **Wiring:** each token carries a `status` claim (list URL and index). Indexes are assigned at random within the
     list, so a list reveals nothing about any one principal.
   - **Rule for verifiers:** an offline verifier that has checked the signature must also check the list, and treat a
     list older than its `max-age` as unknown.
5. **The fiduciary's terms require a check before each use:** either the live check, or the token plus a current
   status list. Webhooks (§6.3) tell fiduciaries sooner, but don't replace the check.
6. **Availability:**
   - **SLO:** live verification has a per-cell objective of 99.95% monthly, an estimate to validate.
   - **Topology:** the revocation set is replicated to read replicas in each availability zone, and the status list is
     served from in-cell storage behind a cache.
   - **Fail-closed or fail-open:** the regime profile records which applies when status can't be determined. The
     default is fail-closed, and `PHI` and `CHILD` purposes are always fail-closed.
   - **Staleness bound:** within a token's lifetime, a consent withdrawn while the cell is unreachable can be honoured
     only by a verifier holding a still-valid token. The token lifetime is therefore the maximum staleness, stated to
     the DPO and in the fiduciary terms.

## Options considered

- **Keep long tokens; rely on the live check and webhooks:** a fiduciary that verifies offline would still honour a
  withdrawn consent for months.
- **No offline verification at all:** every check depends on the platform being up, so an outage stops every
  fiduciary.
- **OCSP-style per-token status queries:** each query tells the platform which consent a fiduciary is using right now,
  which is more data than a consent manager should collect. Status lists avoid that.

## Consequences

- Withdrawal takes effect at once for live verifiers, and within the token lifetime for offline verifiers. Both bounds
  are stated and configurable.
- Fiduciaries must refresh tokens and read status lists; the DF SDK and API documentation will show how.
- Token issuance volume rises with refreshes; issuance is cheap (an RSA signature) but is now capacity-planned.
- ADR-006's `exp` = validity and ADR-013's "for fiduciaries who verify tokens themselves" are superseded as above.

## Compliance impact

DPDPA s.6(4), (6); GDPR Art. 7(3); HIPAA §164.508(b)(5); DPDP Rules First Schedule B7 (safeguards). Data minimisation
in verification (status lists rather than per-token queries).
