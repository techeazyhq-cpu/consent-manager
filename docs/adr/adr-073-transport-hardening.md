# ADR-073: Hardening the edge and the traffic between pods

- **Status:** Accepted
- **Date:** 2026-11-08
- **Issue:** #99

## Context

TLS already ends at the ingress (cert-manager, `ingress.tls`), the web apps send HSTS and the usual headers, and the gateway has
per-plan quotas and sign-up limits. What was missing: the gateway's own responses carried no HSTS, CSP or Permissions-Policy
(Spring Security adds HSTS only to requests it sees as secure, and TLS ends before it); the ingress had no request-rate floor or
body-size limit; traffic between pods was plain HTTP, kept in check by network policies only; and the metrics port added in
ADR-070 had no network policy rule to allow a scraper.

## Decision

1. **The gateway sets security headers on every response,** errors included (`SecurityHeadersWebFilter`): HSTS for a year with
   subdomains, `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, a restrictive Permissions-Policy and
   `Cross-Origin-Opener-Policy: same-origin`. A header a route or service already set is kept. Set unconditionally, not on "secure"
   requests, because a browser ignores HSTS over HTTP.
2. **The ingress is hardened by values** (`ingress.hardening`): forced HTTPS redirect, a 2 MB body limit, and a per-address floor
   of 50 requests a second and 40 connections, ahead of the gateway's quotas. The controller's own settings (TLS 1.2 and 1.3
   only, HTTP/2 with ALPN) are its ConfigMap's, and the runbook says what to set.
3. **Mutual TLS between pods is the mesh's job** (`mesh.provider`): `istio` renders a STRICT `PeerAuthentication` for the release,
   `linkerd` injects the sidecar and admits only authenticated clients. The services do not terminate TLS themselves: certificates
   for nine services, their rotation and identity are what a mesh does, and a hand-rolled version would be worse than one.
4. **Scraping is allowed explicitly:** `networkPolicy.metricsNamespaceLabels` admits a monitoring namespace to the services' ports
   and the gateway's management port (closing ADR-070's open item).

## Not yet / limits

- **The mesh is not installed by the chart.** With `mesh.provider: none` (the default) traffic between pods is unencrypted.
  The chart is checked to render with Istio and Linkerd; it has not been run on a cluster with either.
- **Under Istio STRICT, probes and the ingress controller** need care: the controller must be in the mesh, and probes are rewritten
  by the sidecar. The runbook lists them.
- **Pods outside the mesh** (Postgres, Redis, Pulsar, Keycloak in the common namespace) are not covered by this release's policy.
  Their TLS is a datastore setting (`sslmode`, `redis.ssl`).
- **The rate floor is per address,** so many clients behind one NAT share it; raise it for such a fiduciary rather than remove it.
  The gateway's quotas by plan remain the real limit.
- **No certificate pinning or OCSP stapling** configuration; those are the controller's.

## Options considered

- **Terminating TLS in each service:** nine certificates to issue and rotate, a keystore in each image and a Spring change per
  service, to get what a mesh gives with no code.
- **HSTS only from Spring Security:** never sent, because it does not see the request as secure behind the ingress.
- **Rate limits only in the gateway:** a flood would still cost a gateway pod's CPU before it was refused; the ingress is cheaper.
