# VAPT and performance: Data Fiduciary API

- **Date:** 19 October 2026; load rerun 7 November 2026 (V-03, [Performance](#performance))
- **Target:** the local stack (`infra/docker-compose.yml`), gateway at `localhost:9000`, with notification-service
  beside it. No cloud environment was tested.
- **Build:** `main` after #214 (API keys and signed requests, ADR-041) and #215 (deployment, ADR-042), plus the fixes
  below. The load rerun ran on `main` after #264 (Spring Boot 4, ADR-056; common servers, ADR-053).
- **Kit:** [`tests/`](../../tests/README.md). Everything here can be rerun from it.

## Summary

| | Result |
|---|---|
| Scripted penetration checks (`tests/security/vapt.mjs`) | **68 of 68 pass** after fixes. The first run passed 57 of 61 |
| OWASP ZAP API scan, from the OpenAPI spec, through signed requests | 112 rules pass, 0 fail; 3 warnings remain, triaged below |
| Trivy, the eight service images | 16–20 fixable HIGH/CRITICAL per image before; 2–4 after, all accepted with an expiry and since fixed (V-13). No secrets |
| Load at 50, 75 and 100 requests a second (expected mix) | **0 failures** at each; at 100, verify p95 51 ms. The pool deadlock (V-03) is fixed |
| Ramp to the knee | Clean to about 125 requests a second; beyond, Postgres (1 CPU here) saturates. Verify answered DENY there (V-17, since fixed: now 503) |

Seventeen findings: 1 critical, 3 high, 6 medium, 3 low, 2 accepted, 1 fixed by an upgrade (V-13), 1 false positive.
Thirteen are fixed, the CI gate is in place, two are accepted, and one is open (V-11).

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
| V-03 | High | Under a backlog, cm-cns-cmd-svc deadlocked its own connection pool and stopped answering until restarted | Fixed; 0 failures at 100 requests a second (rerun 2026-11-07) |
| V-04 | Medium | Known HIGH and CRITICAL vulnerabilities in every service image | Fixed; the 4 accepted until 2027-01-31 were fixed in #260 and #261 (V-13) |
| V-05 | Medium | Oversized or control-character input reached the database as a 500 | Fixed |
| V-06 | Medium | One batch with a control-character notice broke its fiduciary's batch list for good | Fixed |
| V-07 | Medium | The read model lost events that arrived before their request (#219) | Fixed in #221 |
| V-08 | Medium | Fiduciaries' API client secrets were stored in plain text (#220) | Fixed in #221 |
| V-09 | Low | An ERROR logged after every consent check, thousands a minute under load (#218) | Fixed in #221 |
| V-10 | Low | No `Cross-Origin-Resource-Policy` on API responses | Fixed (`same-site`); `same-origin` not adopted |
| V-11 | Low | df-reg's `/internal/**` relies on network isolation; any pod of the release can reach it | Open, #99 |
| V-12 | Accepted | A signing key needs signatures on writes only, so a leaked key alone can read | Accepted, as notification-service |
| V-13 | Fixed | Spring WebFlux (Spring Boot 4, ADR-056) and the Pulsar client's bundled BouncyCastle (Pulsar 4.0.14) | #216, #217 |
| V-14 | False positive | ZAP's SQL injection on `/api/aud/activity` | Closed |
| V-15 | Critical | Anyone signed in, a fiduciary's API key included, could approve, decline or withdraw any consent by its id | Fixed (ADR-044) |
| V-16 | Accepted | Two HIGH advisories without a patched release in the mobile app's build tooling (braces, node-forge) | Accepted until 2026-12-31 |
| V-17 | Medium | Overloaded, the PEP answers a valid consent with DENY (403 `consent-not-verified`), not 503 | Fixed (ADR-057); confirmed by the ramp, 0 of 16,209 refusals a 403 |
| V-18 | Medium | The receipt job read every consent event and receipt every 2 s, a cost that grew with each consent ever recorded | Fixed (V7, a queue of events awaiting receipts) |

### V-18: a receipt job that grew with history (Medium)

Found on 7 November 2026, when the mix at 100 a second that had passed that morning failed 11% in the afternoon on
the same settings, with Postgres's one CPU at its limit. Nothing was stuck or backed up, and no table was large
(43,000 consent events). `pg_stat_statements` showed where the time went:

| Statement | Share of Postgres's time | Mean |
|---|---|---|
| `aud.chain_head … for update` (the audit chain's head, one writer at a time) | 23% | 7 ms |
| notification-service's three `notification_message` updates (it shares this Postgres, ADR-053) | 24% | 4–10 ms |
| **cm-cns-cmd-svc's receipt job: events without a receipt** | **11%** | **158 ms** |
| df-reg's nonce insert | 7% | 0.4 ms |

`CnsRcptJob` asks every 2 s for the events still waiting for a receipt (ADR-025). It found them by joining every
record event to every receipt: two full table reads and a hash join per pass, so its cost grew with each consent ever
recorded. It was 30 ms a pass on an idle stack and 158 ms under load at 43,000 events; the day's load tests had
multiplied the tables since the morning. In production, at millions of consents, it would take Postgres down on its
own.

**Fix:** a queue, `cns_cmd.cns_rcpt_pending` (migration V7). An event joins it in the statement that appends it, and
leaves it in the statement that saves its receipt, so a pass reads only what is waiting. The migration queues the
events recorded before it without a receipt. `CnsRcptQueueIT` covers the backfill, the queue, a redelivered event
(queued once), and leaving with the receipt.

**Measured, the same mix at 100 a second, Postgres at 1 CPU, after a three-minute warm-up:**

| | Before | After (two runs) |
|---|---|---|
| Failed | 10.7% (1,232 of 11,496) | 0.01% and 0% (1 of 12,063; 0 of 12,031) |
| p95 token / verify | 8.0 s / 2.6 s | 126 / 137 ms and 379 / 219 ms |
| Postgres CPU, median | about 100% | about 58% |
| Postgres's statement time in the 2 minutes | 61.5 s | 31.7 s |

The receipt query left the list; saving a receipt, now with the queue's delete, takes 1.2 ms. The audit chain's
lock wait fell from 7 to 1.9 ms with Postgres no longer saturated. It still serialises every audit entry, by design
(the hash chain), and is now the largest single cost.

### V-17: overload reported as DENY (Medium)

Found by the load rerun on 7 November 2026. In the ramp past about 150 requests a second, 13,043 consent checks came
back 403 `consent-not-verified`. All of them were for a consent the principal had given and not withdrawn.

`PepVerifyGatewayFilter` fails closed, as ADR-016's table decides for the `pdp` breaker ("Deny (fails closed,
ADR-006)"). It fails closed *as a decision*: when the PDP call times out, errors or finds the breaker open, `allowed()`
falls back to `false`, and the caller gets the same 403 as for a withdrawn consent. In the ramp, OPA's CPU fell to 0 as
Postgres saturated: the breaker had opened, and every check was answered DENY without asking OPA.

A fiduciary can't tell "no consent" from "try again". One that acts on the answer stops processing, or asks the person
again, for consent they gave; in an outage that reaches every principal at once. Asking again for consent already given
is the nagging ADR-028 rules out.

**Fix** ([ADR-057](../adr/adr-057-an-undecided-consent-check-is-503-not-deny.md), amending ADR-016's `pdp` row):
- Still never an allow, but a check that can't be made is now 503 `urn:cm:problem:consent-check-unavailable` with
  `Retry-After` (the breaker's 30 s open-state wait when it is open, otherwise 1 s). A decision stays 403
  `consent-not-verified`: no token, a bad or expired one, another fiduciary's, another purpose, or not `ISSUED`.
- OPA tells the two apart. `cns_authz.rego` calls cm-cns-cmd-svc with `raise_error: false` and publishes
  `decision := {allow, undetermined}`, which the PEP queries. A JWKS that can't be had, or a live status that times
  out or comes back 5xx or 429 for a token that otherwise grants, is `undetermined`. A 404 is still a deny, and a token
  that doesn't grant is denied even while the status can't be had.
- The PEP treats its own failures the same way: OPA unreachable, slower than 2 s, failing, behind an open breaker,
  or answering without a decision.
- Covered by `opa test` (a status timeout and 503, a JWKS timeout and 503), `PepVerifyGatewayFilterTest` (allow,
  deny, undetermined, unreachable, too slow, breaker open), `GwPdpDownTest`, and `GwPepPdpIT` against a real OPA with
  the status endpoint answering 503.

**Confirmed under load** (the ramp rerun below): past the knee, 16,209 consent checks were refused, every one 503
`consent-check-unavailable`, and none 403. A first run had 3 × 403; they were the test's own: a VU whose token refresh
had just failed sent its next verify with no token, which is rightly denied. `df-api.js` now keeps an in-date token
when a refresh fails, and skips the verify when it has none, so every verify carries a consent in force.

### V-16: the mobile app's build tooling (accepted)

The DP mobile app (ADR-050) brought Expo's toolchain into the frontend workspace, and with it three HIGH advisories
in transitive dependencies. CI's `pnpm audit` and Trivy fail on HIGH.

- **source-map-js** (GHSA-68fv-2mgg-jv7q), through Metro's PostCSS: fixed by a pnpm override to 1.2.2.
- **braces** (GHSA-vfj7-8cjw-p6xm, stack exhaustion on hostile glob patterns) and **node-forge**
  (GHSA-86w9-cpqp-85rv, RSA PKCS#1 v1.5 signature verification): neither has a patched release, and both reach only
  Expo's build-time tooling, never the app's bundle:
  - braces is used by Metro's file watcher, on the developer's own patterns
  - node-forge is used by `@expo/cli` for development HTTPS and update signing, which this project doesn't use

  Both are ignored in `frontend/pnpm-workspace.yaml` (`auditConfig.ignoreGhsas`). Recheck them by 2026-12-31, or
  sooner if a fix ships.

### V-15: consent given by someone other than the principal (Critical)

Found while building partial consent (ADR-044), not by the test run. `approve`, `deny` and `revoke` checked only that
the caller was signed in. A fiduciary could raise a request naming a principal's `dpId` (from its own read model),
then approve it with its own API key, and the platform issued a consent token for consent the person never gave.
Another principal could also decline or withdraw someone else's consent by its id.

**Fix:**
- Only a principal's token can approve, decline or withdraw. A fiduciary's people and systems get 403
  `principal-only`.
- A principal who isn't the one the record names gets 404, as if it didn't exist.
- Covered by `DecideCnsReqTest` and check PRN-01.

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

**Measured after the fix (7 November 2026):** the mix at 50, 75 and 100 requests a second, 2 minutes each, with
0 failures, and nothing stuck afterwards. See [Performance](#performance).

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
  Fixed 2026-11-04: Spring Boot 4.0.9 brings WebFlux 7.0.10 (ADR-056).
- **BouncyCastle 1.84** nested inside the Pulsar client's own jar, out of Gradle's reach (#217). Fixed 2026-11-04:
  `pulsar.version` 4.0.14 nests 1.86.

Both are out of `.trivyignore`, so CI's image scan checks them again.

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

**Since ADR-043**, requests and batches name a published purpose version instead of carrying their own notice and
data categories. INP-04 and INP-08 to INP-11 now send the same hostile input to the purpose endpoints, where that text
now lives. PRP-01 checks that one fiduciary can't read, publish or ask for another's purpose. PRP-02 checks that a
superseded version is refused.

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
- **Webhooks (ADR-047), added 2026-10-20 against the local stack:**
  - **SSRF:** run with private addresses refused, as in production. Endpoints on loopback, `localhost`,
    `host.docker.internal`, the cloud metadata address `169.254.169.254` (also as `::ffff:169.254.169.254`),
    `10.0.0.1`, `100.64.0.1`, `::1`, and the stack's own `postgres` and `keycloak` are all refused with 400. So are
    `http`, `ftp`, credentials in the URL and hosts that don't resolve. The same check runs again before every send,
    and redirects aren't followed.
  - **Another fiduciary's webhooks:** reading, rotating, deleting or replaying another fiduciary's endpoint or
    delivery gets 404, and its lists are empty. A principal gets 403.
  - **Signatures:** every message verified by an independent receiver with only the secret. After a rotation, both
    signatures verify for 24 hours.
  - **Secrets:** never listed, shown once, and sealed at rest (`v1:` AES-GCM); the plain secret isn't in the database.
  - **No personal data:** no message carries the principal's id or contact.

## Performance

These numbers show behaviour and bottlenecks on a laptop; they are not production capacity.

### Rerun, 7 November 2026

**Environment:** one Windows laptop, Docker Desktop's VM at 10 GiB and 12 CPUs. One instance of each service at
`CM_SVC_MEM=768m CM_SVC_CPUS=2`. The common servers (ADR-053) keep their ADR-052 limits: **PostgreSQL 1 CPU**, Pulsar
standalone, Keycloak and Mailpit. notification-service runs beside them in its shared mode and delivers every
notification. `tests/perf/run-benchmarks.sh` as in this change: a one-minute warm-up first, then each run.

**The expected mix, 2 minutes each:**

| Rate | Requests | Failed | verify p95 | read p95 | token p95 | raise p95 |
|---|---|---|---|---|---|---|
| 50/s | 6,032 | 0 | 27 ms | 24 ms | 24 ms | 39 ms |
| 75/s | 9,167 | 0 | 64 ms | 49 ms | 54 ms | 91 ms |
| 100/s | 12,062 | 0 | 51 ms | 40 ms | 61 ms | 84 ms |

Every threshold passed, at each rate, with room to spare: p95 300 ms for verify and reads, 500 ms for tokens, 800 ms
for new requests. At 100 a second the median verify is 22 ms and p99 94 ms. This closes V-03.

**A cold start isn't a fair measurement.** The same mix at 50 a second straight after the stack started failed 18% of
requests, with p95s of 4 to 7 s: for the first minute the gateway and cm-cns-cmd-svc ran at their 2-CPU limit while the
JVMs compiled. The script now warms up for a minute first, and doesn't report it.

**API key against OAuth, 50 reads a second each, 1 minute:**

| Read | Median | p95 | p99 |
|---|---|---|---|
| with an API key (df-reg checks the key and signature, then the gateway exchanges it) | 14.3 ms | 19.9 ms | 31.1 ms |
| with a bearer token | 9.4 ms | 12.6 ms | 30.2 ms |

The key's check costs about 5 ms. The bearer side used a DF Console member's token. Since ADR-048, members have their
own limit of 20 a second, so 1,795 of its 3,000 reads got 429, exactly 20 a second through. That is the limit working,
and the comparison was rerun with the API client's own token (below).

**API key against OAuth, done fairly (7 November 2026, OPA at 1 CPU):** the bearer side is now fiduciary B's
`df-api-<dfId>` client-credentials token, the one the gateway exchanges an API key for (ADR-041). Both sides count
against the plan's quota, PREMIUM's 500 a second. The same 50 reads a second each, plus 50 JWKS fetches, 1 minute,
three times after a warm-up:

| Request | Median | p95 | p99 |
|---|---|---|---|
| read with an API key | 14.6–15.3 ms | 22.8–25.0 ms | 44–78 ms |
| read with the API client's bearer token | 9.5–9.9 ms | 14.7–15.7 ms | 35–62 ms |
| JWKS (public, no authentication: the gateway's floor) | 5.7–5.9 ms | 8.2–8.5 ms | 12–37 ms |

27,004 requests, none failed, no 429. An API key costs about 5 ms at the median and 9 ms at p95 over a bearer token:
df-reg's check of the key and signature, and the exchange. The read itself, through the gateway to cm-cns-qry-svc,
is about 4 ms over the gateway's floor. Production hands fiduciaries keys only (the client secret stays in df-reg),
so the key's figures are the ones they see. The short cache of verified keys at the gateway, suggested under "Where
the limit is", would close most of the gap.

**The ramp, 10 to 450 a second over 4 minutes:**

| Offered | Failed | p95 | |
|---|---|---|---|
| up to 107/s | 0% | ≤ 123 ms | |
| 123/s | 0% | 112 ms | |
| 135/s | 0% | 422 ms | the knee: latency climbs |
| 167/s | 22% | 1.7 s | PDP breaker opens; verify answered DENY (V-17, fixed since: now 503) |
| 200–300/s | 52–61% | 0.3–4 s | about 85–125 a second succeed |

**Where the limit is:** PostgreSQL, at its 1-CPU limit from about 125 a second. The gateway (≈ 100%) and df-reg
(≈ 95%) were each about half their 2 CPUs, and OPA, Redis and Pulsar were light. Past the knee, Postgres's latency
reaches the PDP's live-status lookup; its breaker opens, and from then on every consent check was answered DENY without
asking (V-17). Raises, reads and token refreshes fail as 503, which is correct; since the V-17 fix, verify does too (below).

df-reg works almost as hard as the gateway: every API-key request asks it to check the key and the signature. A short
cache of verified keys at the gateway would take that load off, if the knee is raised past Postgres.

### Before the pool fixes, 19 October 2026

**Environment:** Docker Desktop's VM had 7.5 GiB and ran everything: one instance of each service, one PostgreSQL,
Pulsar standalone, Keycloak, OPA, and notification-service with its own Pulsar and PostgreSQL.

#### Expected mix at 50 requests a second, 2 minutes

Measured with the bulkhead fix (V-02), before the pool fixes (V-03).

| Operation | Median | p95 | p99 |
|---|---|---|---|
| verify (PEP, OPA, live status) | 48 ms | 214 ms | 448 ms |
| read one request | 40 ms | 166 ms | 340 ms |
| refresh a consent token | 43 ms | 165 ms | 365 ms |
| raise a request | 67 ms | 222 ms | 396 ms |

6,033 requests, 0 failed. Every threshold passed: p95 300 ms for verify and reads, 500 ms for tokens, 800 ms for new
requests.

#### At 100 requests a second

| Run | Requests | Failed | Notes |
|---|---|---|---|
| 1st, 2 min | 12,533 | 37% | verify p95 982 ms, 10 s outliers; cm-cns-cmd-svc left stuck (V-03) |
| 2nd, 1 min, after a restart | 6,116 | 2% | |
| 50/s again, with the 3 s pool wait | 5,573 | 69% | the pool deadlocked again; led to the root cause in V-03 |

**Where the limit was:** not CPU. The gateway, OPA and Postgres stayed low. It was each service's 10 database
connections being held by background work. With V-03 fixed, the next limits to measure are the pool size against
Postgres's 100 connections, and notification-service, which was the busiest part of the stack as it worked through the
emails the load tests asked for.

### The ramp again, with the V-17 fix, 7 November 2026

The same stack and limits as the rerun above, with the gateway and OPA policy of ADR-057. The ramp after a one-minute
warm-up, twice: as the compose file has it, then with OPA's CPU raised from 0.25 to 1 (a scratch override).

| | Clean up to | Verify refused | of which 403 | Raise, read, token failed | Limit |
|---|---|---|---|---|---|
| OPA at 0.25 CPU | 89/s | 16,209, all 503 | 0 | 4,604 (503, a few 500 and 429) | OPA, at its 0.25-CPU cap |
| OPA at 1 CPU | 145/s (4% at 175/s) | 11,863, all 503 | 0 | 0 | PostgreSQL at 1 CPU, the gateway near 2 |
| OPA at 1 CPU, 128 MB (the compose file since) | 204/s | 11,141, all 503 | 0 | 2,866 (503, 51 × 500), past 270/s | PostgreSQL and the gateway |

**V-17 holds.** Overloaded, verify says "try again", never "no consent".

**OPA at 0.25 CPU is the first limit now.** At about 80 a second its decisions went from a 10 ms median to 0.5–2.4 s,
past the PEP's 2 s; the PDP breaker opened and every check was 503 for its 30 s. With 1 CPU the knee moved back to
where 7 November's was, at Postgres. On 7 November OPA looked light at about 25%, but 25% *is* its cap, so it may
already have been close; these runs don't settle whether ADR-057's policy costs more per decision. Production isn't
affected: the chart gives OPA two replicas and no CPU limit.

**Fixed in the compose file:** OPA now has 1 CPU (`CM_OPA_CPUS`) and 256 MB (ADR-052). The third run, at 1 CPU and
128 MB, was clean to about 200 a second with p95 under 0.9 s, but OPA reached 119 MB of its 128, so the limit is
256 MB.

With OPA at 1 CPU, raises, reads and tokens didn't fail at all; only verify was refused past the knee, as the PDP
breaker should. Latency rose from about 50/s (p95 0.6–4 s) before any failure, as Postgres queued.

### Postgres unconstrained, and connection pools (7 November 2026)

The ramp with Postgres at 4 CPUs (a scratch override; the compose file keeps 1), after a two-minute warm-up, OPA at
1 CPU. Each pool's connections were sampled from `pg_stat_activity` every 2 s, by the client container's address:
"in use" is a connection `active` or `idle in transaction`.

| Ramp | Clean up to | Failed past the knee | Postgres | At their 2-CPU limit |
|---|---|---|---|---|
| Pools as they were (10 each) | 139/s | verify only, all 503 | ≤ 1.7 CPUs | the gateway 1.9, df-reg 1.9 |
| cns-cmd's pool 20, df-reg's 15 | 141/s | verify only, all 503 | ≤ 1.3 CPUs | the gateway 1.9, cns-cmd 1.8, df-reg 1.7 |

**The next limit is CPU in the gateway, df-reg and cm-cns-cmd-svc, not Postgres or the pools.** Postgres never used
two of its four CPUs. Past the knee, cns-cmd's pool was full, 10 of 10, and verify, which waits on it through OPA's
live-status call, failed once a connection took longer than OPA's 2 s. Doubling the pool didn't move the knee: the
20 connections filled the same way, and df-reg's 15. A full pool here is requests queuing behind a busy service, and a
bigger one only lengthens the queue. On one laptop, past about 140 a second, the next step is replicas, as the chart
has.

At the knee the gateway and df-reg each used about 13 ms of CPU per request (1.9 CPUs for about 140 a second). That
was inflated by the overload and by V-18: at 100 a second, with V-18 fixed, df-reg uses about 5.5 ms (see "Profiling
df-reg" below).

Today's ramps found the knee anywhere from 139 to 204 a second with the same settings. Compare runs made back to back,
after a warm-up of two minutes; the first minutes after a restart fail far more (17% at 50 a second).

**Pools, from what was measured.** Past the knee, the most connections in use at once were:

| Service | In use, max | p95 | Pool |
|---|---|---|---|
| cm-cns-cmd-svc | 10 (20 when it had 20) | 10 | 10, unchanged: more doesn't help |
| cm-df-reg-svc | 10 (15) | 6 | 10, unchanged |
| cm-cns-qry-svc | 3 | 2 | 10, unchanged: its projector takes 4 at a time |
| cm-ntf-svc | 0 | 0 | 10, unchanged: its consumer takes 4 at a time |
| cm-aud-svc | 2 | 1 | **5**: its consumer takes one message at a time |
| cm-bil-svc | 1 | 0 | **5**: one relay pass at a time |

Postgres allows 100 connections for every service, notification-service and Keycloak together, and they held 91 at
once. The two smaller pools free 10, so a second replica of a service fits on the local stack.

**With the smaller pools, back on the compose file's limits** (Postgres at 1 CPU, everything restarted): the mix at
100 a second three times in a row failed 31%, then 16%, then 0.04% (6 of 12,028). aud and bil never had more than 2
connections in use, so the pools played no part; the first minutes after a full restart do, Postgres's cache
included. Warm, Postgres's single CPU was at its limit (median 102%) and token p95 was 684 ms, against 61 ms on the
morning of 7 November. That was V-18, fixed since.

### Profiling df-reg (7 November 2026)

JDK Flight Recorder in df-reg (the `profile` settings, 110 s) while the mix ran at 100 a second, every request with an
API key. The recording dump fails in this container ("Unable to complete I/O operation"), but the recording's own
chunk in JFR's repository under `/tmp` holds the same data.

**Its cost is about 5.5 ms of CPU per key check** (0.55 CPU at 100 a second), not the 13 ms read off the overloaded
ramp. GC is negligible: 0.24 s of pauses in 110 s.

**There is no hot spot in our code.** Of the CPU samples, by the library at the top of the stack:

| Library | Share |
|---|---|
| Reactor | 30% |
| R2DBC and the Postgres driver | 17% |
| Netty | 15% |
| Micrometer observations | 8% |
| Spring, web and other | 13% |
| Crypto (AES-GCM, the HMAC) | 5% |
| Spring Security | 4% |
| Jackson | 3% |
| df-reg's own code | 1% |
| JDK and the rest | 6% |

A key check is one HTTP request and four statements through a reactive stack, and that stack is the cost. Two pieces
were waste, and are gone:

- **Observations nobody reads.** No service exports metrics or traces, yet Spring Security observed every filter,
  authentication and authorization of every request. `management.observations.enable.spring.security: false` in all
  eight services; HTTP request metrics are kept.
- **A provider search per decryption.** `AesGcmSealer` called `Cipher.getInstance("AES/GCM/NoPadding")` each time,
  searching every installed provider (2.5% of the samples); df-reg decrypts the key's signing secret and the
  fiduciary's client secret on every check. The provider is now found once. `AesGcmSealerTest` is new.

| | Before | After |
|---|---|---|
| Observations, share of df-reg's samples | 8.3% | 3.8% |
| Crypto, share of df-reg's samples | 4.7% | 2.7% |
| The mix at 100/s, warm | 0 failed | 0 of 12,039 and 0 of 12,064 failed |
| df-reg CPU, median | 54% | 61% and 55% |

About 6% of df-reg's sampled CPU went, but that is less than the difference between two runs of the same settings, so
the container's CPU doesn't show it. Neither change made anything slower.

**What would move the knee is structural, not a query or a function:**
- Replicas of the gateway and df-reg, as the chart has.
- Fewer hops per request. Every API-key request is a call from the gateway to df-reg. A cache of verified keys at the
  gateway, suggested above, can't skip df-reg for a signed request on its own: each needs the key's signing secret for
  the HMAC and a fresh nonce claim against replays. Doing both in the gateway, with the nonces in Redis, would remove
  the hop, at the price of the gateway holding signing secrets (ADR-041). That is a design decision, not a fix.
- The audit chain's lock, now the largest single cost in Postgres (see V-18).

### To finish

- **Fewer hops for API-key requests:** decide whether the gateway should verify keys itself (see "Profiling df-reg").
- **The audit chain's lock:** measure whether it limits throughput before changing how entries are appended.

## Rerunning

See [`tests/README.md`](../../tests/README.md). In short: `setup-keys.mjs`, `vapt.mjs`, `zap-scan.sh`, Trivy as in CI,
then `run-benchmarks.sh`, and finally `teardown-keys.mjs` to revoke the test keys. Run them against the local stack
only.
