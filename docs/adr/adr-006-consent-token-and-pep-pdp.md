# ADR-006: Signed consent tokens, enforced at the gateway with OPA

- **Status:** Accepted, with a known security gap (token signatures are not verified yet)
- **Date:** 2026-08-30 (Phase 1), recorded 2026-10-10

## Context

A fiduciary's systems must check, before each use of personal data, that the principal's consent is still valid for
that purpose. The check has to be cheap and stateless where possible, and must still reflect a revocation made a
moment ago. Authorisation rules should be data, reviewable on their own, rather than code scattered across services.

## Decision

1. **Consent token (`CnsTkn`).** When a request is granted, `cm-cns-cmd-svc` issues a JWT signed with RS256
   (`CnsTknSigner`, Nimbus JOSE) carrying `sub_did`, `fiduciary_id`, `purpose_code`, `access_type` (`ALLOW`/`DENY`),
   `scope` (data categories) and `exp` (the artefact's validity).
2. **Policy enforcement point (PEP): the gateway.** `cm-gw`'s `PepVerifyGatewayFilter` intercepts
   `GET /api/cns-qry/cns-arts/{id}/verify`, takes the token from the `X-Cns-Tkn` header and asks the policy decision
   point; only `allow: true` is forwarded, anything else is a 403.
3. **Policy decision point (PDP): Open Policy Agent.** `infra/opa/policies/cns_authz.rego` allows a `READ` when the
   token's purpose matches the requested purpose, `access_type` is `ALLOW`, it has not expired, **and** the artefact's
   live status, fetched from `cm-cns-qry-svc`'s internal endpoint, is `ISSUED`. The live check is what makes a
   revocation effective immediately (after projection, [ADR-003](adr-003-cqrs-consent-command-and-query.md)).
4. **Gateway-wide controls:** JWT validation against Keycloak for every route, role checks for administrative routes,
   CORS for the three frontends, and a per-fiduciary burst limit (`DfQuotaRateLimitGlobalFilter`): requests per
   second from the fiduciary's subscription in `cm-bil-svc` (cached briefly), counted in Redis, which also feeds the
   monthly usage counter that the subscription gate checks.

## Consequences

- Policy changes are Rego changes, reviewable and testable in isolation.
- Each verification costs one internal call for the live status; that is the price of immediate revocation.
- **Known gap, security-relevant:** the token's signature is not verified anywhere. The policy uses `io.jwt.decode`,
  not `decode_verify`, because the signing key is generated in memory at every start of `cm-cns-cmd-svc` and published
  nowhere. As a result:
  - the purpose, `ALLOW` and expiry claims are attacker-controlled: whoever knows the id of an `ISSUED` artefact can
    present a forged token naming any purpose and pass, as long as the artefact is live;
  - the policy does not check that `fiduciary_id` is the caller's fiduciary;
  - tokens issued before a restart could not be verified even once verification exists.

  The fix, which supersedes this part of the decision: a persistent signing key with rotation, a JWKS endpoint on
  `cm-cns-cmd-svc`, `io.jwt.decode_verify` in the policy, and claims checked against the artefact (its id, purpose and
  fiduciary) and the caller's identity.
