# ADR-083: A key to ask the notification service for a sign-in code

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #105 (the last item of ADR-080's "Not yet" that is about who may ask)

## Context

`POST /internal/otp/send` on cm-ntf-svc sends an email or an SMS, with any code in it, to any address it is given. It is
"internal" only by network position: the cluster's network policy lets Keycloak reach it, and nothing in the service checks who is
calling. Anything else that gets onto the private network, such as a compromised pod or a misconfigured policy, could make the platform
send messages to anyone, with text of its choosing around a code, at the platform's cost and in its name. ADR-080 limits how
many; this decides who.

## Decision

1. **A shared key.** cm-ntf-svc has a setting, `cm.ntf.internal-api-key` (environment `CM_NTF_INTERNAL_API_KEY`). When it is set,
   `POST /internal/otp/send` is answered 401 `internal-key-invalid` unless the request carries the same value in `X-Internal-Key`.
   The check is the first thing done to such a request: before any handler runs and before a limit counts it. It compares
   digests of the two values, so how long the answer takes says nothing about how much of a guess was right.
2. **Keycloak's sign-in step sends it.** A Keycloak *server* setting, `spi-authenticator-cm-otp-authenticator-ntf-api-key`
   (environment `KC_SPI_AUTHENTICATOR_CM_OTP_AUTHENTICATOR_NTF_API_KEY`), never realm data, because a secret does not belong in the
   realm exports, which are plain files in the repository (the same reason as ADR-061's exemption setting).
3. **Both charts take it from a secret, optionally.** `NTF_INTERNAL_API_KEY` in the consent-manager chart's secret reaches
   cm-ntf-svc, and the same key in the common chart's secret reaches Keycloak; both are `optional`, so a cluster that has not set
   one still starts. The two secrets are in different namespaces, so the value is created in both.
4. **Open is loud, not silent.** With no key set, the endpoint works as before, and each side logs a warning at start-up
   (`CM-NTF-INTERNAL-OPEN` in cm-ntf-svc, `CM-OTP-NTF-KEY-MISSING` in Keycloak) that can be alerted on.
5. **The local stack has one** (`cm-local-ntf-internal-key`, overridable by `CM_NTF_INTERNAL_API_KEY`), so the way a deployment runs is
   the way the test kit runs.
6. **Tested** by four unit tests of the filter (the right key passes; a wrong, missing, shorter or longer one is refused before
   the handler; the refusal is a problem that says nothing of the key; other paths and methods are not its business; no key set
   means open) and one of the sender (the header is sent when a key is set, and not when blank); and live: 401 with none or a
   wrong key and 200 with the right one; with Keycloak given a wrong key, a sign-in sends no code; with the right key, the
   sidecar, notification-preference and terms end-to-end scripts, which sign people in, pass.

## Not yet / limits

- **One shared secret, not a credential per caller.** There is one caller today. If another appears, it should have a key of its
  own, so one can be withdrawn without the other.
- **No rotation without a gap.** Changing the key means changing it in both places, and a sign-in in between fails once. The
  service could accept two keys during a change; it does not.
- **Optional by default.** A deployment that never sets it stays open, with only a warning. Making it required in the chart would
  stop an upgrade of a cluster that has not yet created the secret.
- **Only `/internal/otp/send`.** The other `/internal/**` endpoints of other services (quota checks, sub-eligibility, status) keep
  relying on the network policy; so does the gateway's call to the PDP.
- **The key travels in a header over plain HTTP inside the cluster,** as every service-to-service call does; the mesh of ADR-073
  is what encrypts it.

## Options considered

- **Mutual TLS between Keycloak and cm-ntf-svc only:** right with a mesh (ADR-073); until then there is no certificate authority to
  issue the certificates.
- **A signed request (HMAC) like the fiduciary API clients use (ADR-041):** protects against replay as well, and needs a body
  digest, a timestamp and a nonce store on this side. A key is the first step; the signed form is available when it is wanted.
- **A Keycloak service-account token:** the realm is the identity provider that is asking, so it would need cm-ntf-svc to trust
  its own caller's token issuer for this one path, and a token request at every sign-in.
