# VAPT and performance: Data Fiduciary API

- **Date:** 19 October 2026
- **Target:** the local stack (`infra/docker-compose.yml`), gateway at `localhost:9000`, with notification-service
  beside it. No cloud environment was tested.
- **Build:** `main` after #214 (API keys and signed requests, ADR-041) and #215 (deployment, ADR-042), plus the fixes
  below.
- **Kit:** [`tests/`](../../tests/README.md). Everything here can be rerun from it.

## Summary

| | Result |
|---|---|
| Scripted penetration checks (`tests/security/vapt.mjs`) | **68 of 68 pass** after fixes. The first run passed 57 of 61 |
| OWASP ZAP API scan, from the OpenAPI spec, through signed requests | 112 rules pass, 0 fail; 3 warnings remain, triaged below |
| Trivy, the eight service images | 16–20 fixable HIGH/CRITICAL per image before; 2–4 after, all accepted with an expiry. No secrets |
| Load at 50 requests a second (expected mix) | 0 failures; verify p95 214 ms (before the pool fixes) |
| Load at 100 requests a second | Failed: a connection-pool deadlock in cm-cns-cmd-svc, found and fixed. **The rerun is pending** |

Fourteen findings: 3 high, 5 medium, 3 low, 2 accepted, 1 false positive. Ten are fixed, the CI gate is in place,
and one is open and tracked.

## How it was tested

- **Authentication and signing:** two fiduciaries with API keys (`tests/security/setup-keys.mjs`), each signed per
  CM1-HMAC-SHA256: A is Batch Test Co, B is a test fiduciary registered on PREMIUM. A revoked key, a key that doesn't
  require signing, and a consent each fiduciary was given by a test data principal.
- **Scripted checks:** 68 checks across authentication, signing, replay, cross-fiduciary access, privilege, input,
  what responses give away, and abuse. Each states the secure answer before it runs.
- **ZAP:** a scanner can't sign requests, so `zap-scan.sh` runs ZAP through `signing-proxy.mjs`, which signs each
  request with B's key, on a private Docker network. The scan reached everything behind authentication, as an
  integration would.
- **Trivy:** each running service image, HIGH and CRITICAL, fixable only.
- **k6:** `tests/perf/df-api.js` signs every request in the script. Scenarios: the expected mix (60% verify, 20%
  reads, 10% token refresh, 10% new requests), API key against OAuth, and a ramp.

## Findings

| ID | Severity | Finding | Status |
|---|---|---|---|
| V-01 | High | One fiduciary could read another's consent request and its history by id | Fixed |
| V-02 | High | The gateway refused API-key requests with 503 from about 30 at once | Fixed |
| V-03 | High | Under a backlog, cm-cns-cmd-svc deadlocked its own connection pool and stopped answering until restarted | Fixed, rerun pending |
| V-04 | Medium | Known HIGH and CRITICAL vulnerabilities in every service image | Fixed, 4 accepted until 2027-01-31 |
| V-05 | Medium | Oversized or control-character input reached the database as a 500 | Fixed |
| V-06 | Medium | One batch with a control-character notice broke its fiduciary's batch list for good | Fixed |
| V-07 | Medium | The read model lost events that arrived before their request (#219) | Fixed in #221 |
| V-08 | Medium | Fiduciaries' API client secrets were stored in plain text (#220) | Fixed in #221 |
| V-09 | Low | An ERROR logged after every consent check, thousands a minute under load (#218) | Fixed in #221 |
| V-10 | Low | No `Cross-Origin-Resource-Policy` on API responses | Fixed (`same-site`); `same-origin` not adopted |
| V-11 | Low | df-reg's `/internal/**` relies on network isolation; any pod of the release can reach it | Open, #99 |
| V-12 | Accepted | A signing key needs signatures on writes only, so a leaked key alone can read | Accepted, as notification-service |
| V-13 | Accepted | Spring WebFlux and the Pulsar client's bundled BouncyCastle (part of V-04) | #216, #217 |
| V-14 | False positive | ZAP's SQL injection on `/api/aud/activity` | Closed |

### V-01: one fiduciary reading another's requests (High)

`GET /api/cns-qry/cns-arts/{id}` and `/{id}/history` returned any request to any authenticated caller that had its id.
That included another fiduciary's API key or console user. The list endpoint was scoped correctly. Ids are UUIDs, so
an attacker needs one first, which lowers the likelihood but not the impact.

**Fix:** both routes answer only the request's own fiduciary or data principal. Anyone else gets 404, the same as an
unknown id. `CnsQryHandlerTest`; checks IDOR-01, -02, -05.

### V-02: a cap of 25 calls nobody set (High)

Spring Cloud CircuitBreaker wraps every call in a Resilience4j bulkhead, by default 25 concurrent calls with no wait.
Past that a call fails as if its dependency were down.

| Concurrent API-key requests | 20 | 40 | 80 | 160 |
|---|---|---|---|---|
| Before: refused with 503 | 0 | 6 | 39 | not run |
| After | 0 | 0 | 0 | 0 |

**Fix:** every service sets `resilience4j.bulkhead.configs.default` to 1000 calls. The time limiters and breakers
already guard against a slow dependency.

### V-03: the connection pool deadlock (High)

At 100 requests a second, 37% of requests failed, and afterwards cm-cns-cmd-svc answered no request that needed the
database until it was restarted. Three causes compounded:

1. **Overlapping relay passes:** the outbox relays (cns-cmd, df-reg, bil) are `@Scheduled` every second but return at
   once, so a new pass started whether or not the last had finished.
2. **Held connections:** each pass streamed its `select` while publishing, holding that connection, and each row's
   `update` needed a second. Passes took all 10 pooled connections and waited on each other. Overlapping passes also
   published events twice.
3. **Unbounded consumers:** the Pulsar consumers handled up to 256 messages at once against a pool of 10. A backlog
   drained by starving requests, and the nacked messages came back for more.

**Fix:**
- **Relays:** one pass at a time, each batch read in full before publishing. The batch worker reads its lists first
  too.
- **Consumers:** 4 messages at a time (cns-cmd saga, cns-qry projector, ntf).
- **Pool waits:** every service waits at most 3 s for a connection, then fails, instead of holding requests until the
  gateway's 10 s limit.

**Not yet measured after the fix.** Docker Desktop's VM (7.5 GiB) ran out of memory several times during the runs,
which also made the earlier measurements noisy. Rerun `tests/perf/run-benchmarks.sh` before relying on capacity
figures.

### V-04: dependency vulnerabilities (Medium)

Every image carried HIGH and CRITICAL vulnerabilities with fixes available: Netty 4.1.135, Jackson 2.21.4,
BouncyCastle 1.80/1.84, SCRAM 3.2, and Alpine's libexpat. CI's image scan reported them but didn't fail, waiting for
this upgrade.

**Fix:**
- **Patched releases** in `build.gradle.kts`, within the lines in use: Netty 4.1.137, Jackson 2.21.7,
  BouncyCastle 1.85, SCRAM 3.3.
- **OS packages:** `apk upgrade` in the service image, for libexpat.
- **CI gate:** the image scan now fails on CRITICAL. Accepted exceptions live in `.trivyignore`, each with a reason
  and an expiry date.

| Image | Fixable HIGH/CRITICAL before | After |
|---|---|---|
| cm-gw | 16 | 2 |
| The other seven services | 18–20 | 4 |

**Still in the images (V-13):**
- **Spring WebFlux 6.2.19** (2 CRITICAL): fixed only in Spring Framework 7, so a Spring Boot 4 migration (#216).
- **BouncyCastle 1.84** nested inside the Pulsar client's own jar, out of Gradle's reach (#217).

### V-05: input that reached the database (Medium)

Each of these returned a 500, not a 400:
- **Sizes the database can't hold:** a purpose over 64 characters, a title over 255, a recipient over 255.
- **Unbounded lists:** 10,000 data categories were accepted.
- **NUL characters:** Postgres rejects them.
- **Domain refusals:** any `IllegalArgumentException` from the domain, such as no data categories.

**Fix:**
- **New requests are bounded:** at most 50 data categories, codes of 64 characters, a title of 255, a notice of 20,000.
- **No control characters**, except a notice's line breaks and tabs.
- **Domain refusals answer 400** `invalid-request`.

The checks run only when a request is made, so stored requests always read back. Checks INP-07 to INP-10.

### V-06: a batch that broke its fiduciary's list (Medium)

Found by ZAP. The batch template checked that its notice wasn't blank, then trimmed it. `"\u0001"` isn't blank to
`isBlank()`, but `trim()` removes it, so an empty notice was stored. Reading that batch back ran the same check, failed,
and every listing of that fiduciary's batches returned 500 from then on.

**Fix:** the template normalises before it checks, and its constructor, used to read stored batches, no longer
validates. Check INP-11.

### V-07 to V-09

Fixed in #221; see its description. In short:
- **V-07:** the projector retries events that arrive before their request, and ignores ones older than the row.
- **V-08:** client secrets are sealed with the AES-GCM sealer from ADR-041.
- **V-09:** the PEP no longer writes over an answered response.

### V-10: cross-origin isolation (Low)

The gateway now adds `Cross-Origin-Resource-Policy: same-site`. ZAP prefers `same-origin` and still warns. That would
stop the web apps, served from a sibling host, from showing fiduciaries' logos through `<img>`. Accepted.

### V-11: df-reg's internal routes (Low, open)

`/internal/api-keys/verify` and `/internal/dfs/{id}/active` are `permitAll` in df-reg and rely on not being routed by
the gateway, which holds (check PRIV-09). In Kubernetes, the chart's NetworkPolicies let every pod of the release reach
every other. A compromised pod could use the verify route as a signature oracle, though it still needs a valid key.
mTLS, or a policy that admits only the gateway, belongs to #99.

### V-12: unsigned reads (accepted)

A key that requires signatures refuses unsigned writes (SIG-01) but answers unsigned reads (SIG-14), exactly as
notification-service's NS1 does. A leaked key alone can read a fiduciary's requests, but can't create, change or
replay anything.

### V-14: ZAP's SQL injection warning (false positive)

ZAP flagged `limit`, then `before`, on `/api/aud/activity` with boolean payloads. Both parameters are parsed as
numbers, fall back to defaults when they aren't, and are bound as parameters. The true and false payloads return the
same 50 rows as plain `50` and `abc`. ZAP saw differences because the activity log grows while it scans: its own
requests add entries.

## What held

All of these passed from the first run:
- **API keys:** no key, unknown, revoked or malformed keys, a key in the query string, and key plus bearer together
  are all refused.
- **Forged tokens:** a forged `alg: none` bearer is refused.
- **Signatures:** changing the body, path, query or method after signing fails. So do another secret, a truncated
  signature, timestamps 301 s off either way (290 s is accepted), and malformed timestamps or nonces.
- **Replay:**
  - a signed request sent twice: the second gets 409
  - the same request sent 25 times at once: exactly one 201 and 24 × 409
  - a used nonce with a new timestamp: refused
  - a captured request replayed with another key: refused
- **Privilege:** an API key can't list or issue keys, invite owners, or reach any staff route. The internal key check
  isn't reachable through the gateway, path tricks included.
- **Consent tokens:** another fiduciary's token, another purpose, or a tampered payload are all refused at verify.
- **Responses:** no stack traces, versions or SQL in errors, no CORS for unknown origins, actuator endpoints closed,
  `nosniff`, `no-store` and `DENY` framing on API responses. A 13 MB signed body gets 413.
- **Abuse:** 300 bad-key requests in a burst get 401, and other callers are unaffected.

## Performance

**Environment:** one Windows laptop. Docker Desktop's VM has 7.5 GiB and runs everything: one instance of each service,
one PostgreSQL, Pulsar standalone, Keycloak, OPA, and notification-service with its own Pulsar and PostgreSQL. These
numbers show behaviour and bottlenecks; they are not production capacity.

### Expected mix at 50 requests a second, 2 minutes

Measured with the bulkhead fix (V-02), before the pool fixes (V-03).

| Operation | Median | p95 | p99 |
|---|---|---|---|
| verify (PEP, OPA, live status) | 48 ms | 214 ms | 448 ms |
| read one request | 40 ms | 166 ms | 340 ms |
| refresh a consent token | 43 ms | 165 ms | 365 ms |
| raise a request | 67 ms | 222 ms | 396 ms |

6,033 requests, 0 failed. Every threshold passed: p95 300 ms for verify and reads, 500 ms for tokens, 800 ms for new
requests.

### At 100 requests a second

| Run | Requests | Failed | Notes |
|---|---|---|---|
| 1st, 2 min | 12,533 | 37% | verify p95 982 ms, 10 s outliers; cm-cns-cmd-svc left stuck (V-03) |
| 2nd, 1 min, after a restart | 6,116 | 2% | |
| 50/s again, with the 3 s pool wait | 5,573 | 69% | the pool deadlocked again; led to the root cause in V-03 |

**Where the limit was:** not CPU. The gateway, OPA and Postgres stayed low. It was each service's 10 database
connections being held by background work. With V-03 fixed, the next limits to measure are the pool size against
Postgres's 100 connections, and notification-service, which was the busiest part of the stack as it worked through the
emails the load tests asked for.

### To finish

- Rerun `KEYS_FILE=… tests/perf/run-benchmarks.sh` on a stack with more memory (`.wslconfig` with `memory=10GB`), and
  add the results here:
  - the mix at 50, 75 and 100 requests a second
  - API key against OAuth
  - the ramp to the knee
- Then set the pool size per service from the measured concurrency.

## Rerunning

See [`tests/README.md`](../../tests/README.md). In short: `setup-keys.mjs`, `vapt.mjs`, `zap-scan.sh`, Trivy as in CI,
then `run-benchmarks.sh`, and finally `teardown-keys.mjs` to revoke the test keys. Run them against the local stack
only.
