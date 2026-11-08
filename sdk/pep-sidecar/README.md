# Consent sidecar

A small service a fiduciary runs **next to its own services** so each of them asks one local question before using personal
data: *may I use this consent for this purpose, right now?* ([ADR-076](../../docs/adr/adr-076-fiduciary-consent-sidecar.md)).
It speaks the Consent Manager's public API, signs its requests with the fiduciary's API key, keeps the answer for a few
seconds, and says plainly when it cannot know. Zero dependencies, Node 20+.

## Use

```bash
docker build -f sdk/pep-sidecar/Dockerfile -t cm-pep-sidecar .
docker run --rm -p 127.0.0.1:8484:8484 \
  -e CM_GATEWAY=https://api.example.com -e CM_API_KEY=... -e CM_SIGNING_SECRET=... -e CM_FIDUCIARY_ID=... \
  cm-pep-sidecar
```

```bash
curl -s -X POST http://127.0.0.1:8484/v1/check -d '{"cnsArtId":"<consent id>","purpose":"ORDER_UPDATES"}'
# 200 {"decision":"ALLOW","basis":"live"}
# 200 {"decision":"DENY","reason":"consent-not-verified","basis":"live"}
# 503 {"decision":"UNAVAILABLE",...}   with Retry-After: neither a yes nor a no: wait and ask again
```

In Kubernetes run it as a second container in the pod of the service that needs it, on `localhost`; the API key comes from a Secret.

| Setting | Default | |
|---|---|---|
| `CM_GATEWAY`, `CM_API_KEY`, `CM_SIGNING_SECRET` | required | the gateway and the fiduciary's signing API key |
| `CM_FIDUCIARY_ID` | none | when set, a token for another fiduciary is never accepted offline |
| `PEP_CACHE_SECONDS` | 5 | how long a live answer is reused; this is how late the sidecar can be to a withdrawal |
| `PEP_GRACE_SECONDS` | 0 (off) | see below |
| `PEP_NO_GRACE_PURPOSES` | none | purposes that are never bridged, for example health or children's data |
| `PEP_MODE` | `live` | `list` answers from the consent token and the fiduciary's signed status list instead of calling the Consent Manager on every check |
| `PEP_STATUS_LIST_ORIGIN` | the origin of `CM_GATEWAY` | the only origin a token's status list may be fetched from |
| `PEP_HOST`, `PEP_PORT` | `127.0.0.1`, 8484 | it answers whoever can reach it; keep it on the host or pod |

## What it guarantees, and what it does not

- **A cached answer is at most `PEP_CACHE_SECONDS` old.** Withdrawal reaches the sidecar's users that soon, plus the Consent
  Manager's own propagation (ADR-027).
- **"Could not check" is never a yes.** With grace off, an unreachable Consent Manager is `UNAVAILABLE` (503).
- **Grace is opt-in and bounded.** With `PEP_GRACE_SECONDS=120`, if the Consent Manager cannot be reached the sidecar may keep
  answering ALLOW for a consent it saw allowed live in the last 120 seconds, but only while the consent token it holds is
  unexpired and **its signature verifies against the platform's published keys**, offline, for this consent, purpose and fiduciary.
  The answer says `"basis":"offline-token"` and `staleUntil`. A withdrawal made during that window is not seen until it
  ends: the stated cost of not stopping your processing for an outage. Tokens live about 15 minutes (ADR-027), so that is also
  the longest grace that can ever apply. A DENY ends grace for that consent, and purposes in `PEP_NO_GRACE_PURPOSES` never get it.
- **List mode** (`PEP_MODE=list`, [ADR-077](../../docs/adr/adr-077-token-status-list.md)) is the fully offline path. It fetches the
  consent token once, checks its signature against the published keys, and reads the token's bit in the fiduciary's signed status
  list, which it fetches again only when the one it holds expires (a minute). A withdrawal is seen within that minute plus the 10
  seconds the platform keeps a built list; a list past its expiry that cannot be replaced is `UNAVAILABLE`, never a yes. The answer
  says `"basis":"status-list"` and `staleUntil`. The platform sees about one request a minute instead of one per check.
- **It does not** hold any state beyond its memory (a restart forgets what it had seen allowed, so grace starts empty), or
  authenticate its callers.

## Tests

```bash
node --test sdk/pep-sidecar/test/*.test.mjs                              # unit tests, no network
KEYS_FILE=/tmp/cm-keys.json node tests/security/e2e-pep-sidecar.mjs      # against the local stack
```
