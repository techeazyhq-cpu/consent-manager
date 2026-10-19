# ADR-041: API keys and HMAC-signed requests against replay, for fiduciaries' systems

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

A fiduciary's systems called the API with OAuth 2.0 client credentials: a `df-api-<id>` client in the `cm-df` realm,
whose bearer tokens prove who is calling for a few minutes.

- **Nothing ties a request to one use.** Anyone who captures a request with a live token (from logs, a debugging
  proxy, a compromised host, a TLS-terminating middlebox) can send it again or alter it until the token expires.
  Replaying a consent request asks a person again, and is counted again.
- **Fiduciaries asked for what TechEazyHQ's notification-service offers:** an API key with HMAC-signed requests.
  Regulated tenants (banks, health, public sector) want message integrity and replay protection in their own audits.

## Decision

1. **API keys per fiduciary, managed in the DF Console** (*Developers → API keys*) by Owners, Administrators and
   Developers. Each key and each change to it is recorded in the audit ledger (ADR-040).
   - **The key:** `cmk_` + 256 random bits, shown once. Only its SHA-256 and its first 12 characters are kept.
   - **The signing secret:** `cms_` + 256 random bits, shown once. It is kept sealed with AES-256-GCM under
     `cm.dfreg.secrets-key`, because every signature has to be recomputed with it. Production takes that key from the
     cloud's key management.
   - **Lifecycle:** a key can require signatures, can stop requiring them, and can be revoked at once. A revoked key
     is never restored.
2. **The signature scheme matches notification-service's**, named `CM1-HMAC-SHA256`:
   - the headers `X-Signature-Timestamp`, `X-Signature-Nonce` and `X-Signature`
   - lower-case hex HMAC-SHA256 over seven lines: the scheme, timestamp, nonce, method, path as sent, query as sent,
     and the hex SHA-256 of the exact body bytes

   Hashing the raw bytes gives clients nothing to canonicalise.
3. **Checked at the gateway, decided by df-reg.** `ApiKeyAuthWebFilter` in cm-gw runs before Spring Security, reads
   the body (up to 12 MB) and hashes it.
   - **df-reg decides.** The gateway sends df-reg (`POST /internal/api-keys/verify`, private network only) the key,
     method, path, query, body hash and signature headers. df-reg holds the keys, sealed secrets and nonces.
   - **df-reg's checks, in order:**
     - the key exists and isn't revoked, and its fiduciary is active
     - if any signature header is present: format, a ±300 s window, a constant-time HMAC comparison, then the nonce
       is claimed (only after the signature checks out, so forgeries can't use up nonces)
     - an unsigned `POST`, `PUT`, `PATCH` or `DELETE` is refused if the key requires signing; reads may go unsigned
   - **Then:** df-reg returns the fiduciary's API client access token (client credentials, cached until shortly
     before expiry), and the gateway forwards the request with that bearer token in place of the key and signature
     headers. The security chain, quotas, the PEP, the services and the audit trail all see an ordinary `df-api`
     token. Nothing downstream changed.
   - **Fails closed:** if df-reg can't be reached, an API-key request is refused (503).
4. **Nonces in Postgres, in df-reg's schema** (`df_reg.api_request_nonce`, primary key `(key_id, nonce)`).
   - **The claim** is one `INSERT … ON CONFLICT DO UPDATE … WHERE expired RETURNING`: atomic across instances, and a
     long-expired row never blocks a nonce. A job purges expired rows every 10 minutes.
   - **Not Redis:** Redis can evict under memory pressure and fails open, and a lost nonce is a successful replay
     (notification-service's ADR-036 reached the same conclusion).
   - **Not in the gateway:** the nonce lives beside the key and secret it belongs to, so the check is one call and the
     secret never leaves df-reg. The gateway stays without a database of its own. This departs from first putting
     nonces in a gateway schema, for that reason.
5. **Errors** are problem+json, with the `title` values `api-key-invalid` (401), `signature-required` (401),
   `signature-invalid` (401), `signature-expired` (401), `request-replayed` (409), `ambiguous-credentials` (400) and
   `payload-too-large` (413).
6. **The contract** is `docs/api/df-api.openapi.yaml` (OpenAPI 3.1, linted).
   - The Postman collection is generated from it (`scripts/make-postman.py`). Its pre-request script signs every
     request, and it includes a replay demonstration.
   - `docs/api/README.md` gives signing code for shell, Python, Node.js and Java.

## Options considered

- **Mutual TLS:** proves the client on each connection, but doesn't stop the certificate holder from replaying, doesn't
  survive TLS-terminating proxies, and needs certificate issuance for every tenant.
- **RFC 9421 HTTP Message Signatures:** standard, but much harder to implement in a shell script, and client libraries
  are uneven. The scheme line leaves room to accept it later.
- **Signing at the services instead of the gateway:** every service would need the body hash and the key store. Here
  one filter handles it for every route.
- **The gateway minting its own tokens:** every service would have to trust a new issuer. Using the realm's own
  client-credentials token changes nothing downstream.

## Consequences

- **Protection:** for a key that requires signing, a captured request can be used once, only within five minutes,
  and can't be altered. A stolen key alone can't write anything.
- **Cost:** one extra internal call per API-key request (one indexed lookup, an AES-GCM open, an HMAC, one insert),
  plus a Keycloak call once per token lifetime per fiduciary. The gateway holds the body in memory while it's checked.
- **Clients:** need NTP-synchronised clocks and a new nonce for every attempt, retries included.
- **Postman** can't sign multipart bodies, which it builds after its scripts run. The collection's upload goes unsigned
  and the guide says so.
- **Plaintext client secret:** the fiduciary's `df-api` client secret is still stored in plain text in `df_reg.df`, as
  activation left it. It should be sealed the same way. That's left for a follow-up.
- **Rotation:** to change a secret, issue a new key and revoke the old one. There's no grace period with two secrets.

## Compliance impact

- Integrity and replay protection for the API that creates consent requests: DPDPA reasonable security safeguards
  (s.8(5)), GDPR Art. 32, ISO 27001 A.8.24/A.8.26.
- Keys are never stored in a usable form, signing secrets are sealed, and every key change is in the audit ledger.
