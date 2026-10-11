# ADR-016: A circuit breaker on every synchronous call to another service

- **Status:** Accepted
- **Date:** 2026-10-11

## Context

The services mostly talk through Pulsar events (ADR-004), but some calls have to be synchronous because the answer
decides the request in hand: the gateway asks OPA for a decision and cm-bil-svc for a fiduciary's burst quota, and
routes each request to a service. cm-cns-cmd-svc asks cm-df-reg-svc and cm-bil-svc before it accepts a consent request.
cm-df-reg-svc provisions a fiduciary's client in Keycloak, cm-ntf-svc sends through notification-service, and
cm-aud-svc anchors checkpoints with a timestamp authority.

Only cm-cns-cmd-svc's two calls had a breaker, on library defaults. The others would wait on a dependency that is
down for the whole timeout, on every request, holding connections while it recovered. Two fallbacks also told the
caller the wrong thing: cm-df-reg-svc being down came back as "fiduciary not active" (404), and cm-bil-svc being
down as "subscription inactive" (402). The PEP turned any error into a deny, including the service behind it failing
after an allow.

## Decision

1. **Every synchronous call between services, and to Keycloak, OPA, notification-service and the timestamp
   authority, goes through a breaker.** The breaker sits in the `-infra` adapter that makes the call (the gateway's
   filters, for cm-gw), through Spring Cloud CircuitBreaker on Resilience4j. Ports and the domain don't know about it.
2. **One breaker per dependency**, named after it, plus one per gateway route (named after the route id, by
   `default-filters`):

   | Caller | Breaker | When it is open or the call fails |
   |---|---|---|
   | cm-gw | `pdp` | Deny (fails closed, ADR-006) |
   | cm-gw | `bil-quota` | Allow, at the last quota known for the fiduciary (fails open: a burst limit, not an authorisation) |
   | cm-gw | each route (`df-reg`, `cns-cmd`, ...) | 503 from `RouteFallbackController` |
   | cm-cns-cmd-svc | `cm-df-reg-svc`, `cm-bil-svc` | 503: the consent request is refused, and the fiduciary is told to retry |
   | cm-df-reg-svc | `keycloak-admin` | 503: the fiduciary stays PENDING, activation can be repeated |
   | cm-ntf-svc | `notification-service` | 503 for an OTP; a Pulsar command is redelivered |
   | cm-aud-svc | `timestamp-authority` | The checkpoint is anchored on a later run |

3. **Only an unhealthy dependency counts against its breaker:** unreachable, timed out, or 5xx/429
   (`cm-res-kit`'s `RemoteFailures`, every breaker's `record-failure-predicate`). A 4xx is the dependency working, so
   a burst of bad requests can't open the breaker on a healthy service.
4. **One shape for "try again later":** a fallback with no safe answer raises `DependencyUnavailableException`
   (`DependencyFallbacks.unavailable`). `cm-res-kit` answers it with 503 `urn:cm:problem:dependency-unavailable` and
   a `Retry-After` of the breaker's open-state wait. Any other error, such as Keycloak's 409, reaches the caller
   unchanged.
5. **Explicit settings, the same in every service:** over the last 20 calls (at least 10), open at a 50% failure
   rate, stay open 30s, then let 3 trial calls through. Time limits are 3s by default, 2s for the gateway's own calls
   on the request path, 10s for a gateway route and for Keycloak provisioning. The time limit is off for
   notification-service and the timestamp authority, whose clients time out each attempt themselves; the
   notification-service breaker wraps the client's retries, so one send counts once.
6. **A route's breaker counts only failures to reach the service, not its responses.** A 503 that a service gives
   for its own dependency passes through, so it doesn't open the route's breaker as well.

## Consequences

- While a dependency is down, callers get a 503 at once instead of waiting out a timeout, and the dependency gets
  room to recover. Clients see the outage as a retryable problem, not as a business decision.
- Fail-closed and fail-open are now one line per breaker, each stated in the table above and covered by a test.
- Breaker settings are repeated in each service's `application.yml`, so a service can tune its own; changing the
  defaults means changing every file.
- Breaker state is not yet published (no health indicator or metrics endpoint). Exposing it is follow-up work.
