# Data Fiduciary API

The API a data fiduciary's systems call to ask people for consent, verify it before using their data, and follow what
happened. The spec is [df-api.openapi.yaml](df-api.openapi.yaml) (OpenAPI 3.1). The Postman collection
[df-api.postman_collection.json](df-api.postman_collection.json) signs every request for you.

| File | What |
|---|---|
| `df-api.openapi.yaml` | The contract: paths, schemas, authentication, signed requests and errors |
| `df-api.postman_collection.json` | Every call, signed by a pre-request script, with tests and a replay demonstration |
| `df-api.local.postman_environment.json`, `df-api.sandbox.postman_environment.json` | `baseUrl`, and empty `apiKey` and `signingSecret` for you to fill in |
| `list.csv` | A sample list for the CSV upload |
| `df-webhooks.asyncapi.yaml` | What is posted to your webhook endpoints (AsyncAPI 3) |
| `redocly.yaml` | Lint rules: `npx @redocly/cli lint docs/api/df-api.openapi.yaml` |

Regenerate the collection after changing the spec: `python scripts/make-postman.py`. The DF Console's Developer
docs page offers these files for download with a quick start filled in with your organisation's details.

## 1. Get an API key

In the DF Console, an Owner, Administrator or Developer opens **Developers → API keys** and issues a key. The key
(`cmk_…`) and its signing secret (`cms_…`) are shown **once**: keep them in your secrets manager. Leave **Require a
signature on every write** on: then a stolen key alone can't change anything.

To integrate without touching real people, do this in the **sandbox** (`http://localhost:9100` locally) with test
contacts such as `asha@sandbox.example`.

OAuth 2.0 client credentials still work (`df-api-<fiduciary id>`, in `Authorization: Bearer`). Send one or the other,
never both.

**Before asking anyone, define and publish a purpose** (ADR-043): `POST /api/cns-cmd/prps-defs`, then
`POST /api/cns-cmd/prps-defs/{code}/publish`, or under **Consent → Purposes** in the DF Console. Requests then name it
as `prpsCode` and `prpsVer`. A change to the purpose is a new version; when it is material (more data, new
recipients, longer retention or validity), requests for the old version are refused with 409
`purpose-version-superseded`. Ask for the new version instead.

## 2. Sign each request

Send `X-API-Key`, plus:

| Header | Value |
|---|---|
| `X-Signature-Timestamp` | The current time in Unix seconds |
| `X-Signature-Nonce` | A **new** random value for every request, 16–64 of `A-Z a-z 0-9 _ -` |
| `X-Signature` | Lower-case hex HMAC-SHA256, keyed by the signing secret, of the text below |

```text
CM1-HMAC-SHA256
<X-Signature-Timestamp>
<X-Signature-Nonce>
<METHOD>
<path, exactly as sent>
<query string without '?', or empty>
<lower-case hex SHA-256 of the exact body bytes>
```

The lines are joined by `\n`, with no newline at the end. Sign exactly the bytes you send: serialise the JSON once,
hash it, and send that string.

### Shell (curl + openssl)

```bash
body='{"extDpRefTyp":"EMAIL","extDpRefVal":"asha@sandbox.example","prpsCode":"ORDER_UPDATES","prpsVer":1}'
ts=$(date +%s); nonce=$(openssl rand -hex 16); path=/api/cns-cmd/cns-reqs
body_hash=$(printf '%s' "$body" | openssl dgst -sha256 -hex | awk '{print $NF}')
sig=$(printf 'CM1-HMAC-SHA256\n%s\n%s\nPOST\n%s\n\n%s' "$ts" "$nonce" "$path" "$body_hash" \
  | openssl dgst -sha256 -hmac "$SIGNING_SECRET" -hex | awk '{print $NF}')
curl -X POST "http://localhost:9100$path" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -H "X-Signature-Timestamp: $ts" -H "X-Signature-Nonce: $nonce" -H "X-Signature: $sig" --data-raw "$body"
```

### Python

```python
import hashlib, hmac, secrets, time

def sign(secret: str, method: str, path: str, query: str, body: bytes) -> dict:
    ts, nonce = str(int(time.time())), secrets.token_hex(16)
    text = "\n".join(["CM1-HMAC-SHA256", ts, nonce, method.upper(), path, query, hashlib.sha256(body).hexdigest()])
    sig = hmac.new(secret.encode(), text.encode(), hashlib.sha256).hexdigest()
    return {"X-Signature-Timestamp": ts, "X-Signature-Nonce": nonce, "X-Signature": sig}
```

### Node.js

```js
const crypto = require('node:crypto');

function sign(secret, method, path, query, body /* Buffer or string */) {
  const ts = Math.floor(Date.now() / 1000).toString();
  const nonce = crypto.randomBytes(16).toString('hex');
  const bodyHash = crypto.createHash('sha256').update(body ?? '').digest('hex');
  const text = ['CM1-HMAC-SHA256', ts, nonce, method.toUpperCase(), path, query ?? '', bodyHash].join('\n');
  const sig = crypto.createHmac('sha256', secret).update(text).digest('hex');
  return { 'X-Signature-Timestamp': ts, 'X-Signature-Nonce': nonce, 'X-Signature': sig };
}
```

### Java

```java
static Map<String, String> sign(String secret, String method, String path, String query, byte[] body) throws Exception {
    String ts = Long.toString(Instant.now().getEpochSecond());
    String nonce = HexFormat.of().formatHex(SecureRandom.getInstanceStrong().generateSeed(16));
    String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    String text = String.join("\n", "CM1-HMAC-SHA256", ts, nonce, method.toUpperCase(), path,
            query == null ? "" : query, bodyHash);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Map.of("X-Signature-Timestamp", ts, "X-Signature-Nonce", nonce,
            "X-Signature", HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8))));
}
```

## 3. What gets refused, and why

| `title` | Status | Meaning |
|---|---|---|
| `api-key-invalid` | 401 | No key, an unknown one, or a revoked one |
| `signature-required` | 401 | The key requires signatures, and this write is unsigned |
| `signature-invalid` | 401 | Headers missing or malformed, or the signature doesn't match the request |
| `signature-expired` | 401 | The timestamp is more than 5 minutes from the server's clock (check NTP) |
| `request-replayed` | 409 | This nonce was already used with this key |
| `ambiguous-credentials` | 400 | Both `X-API-Key` and `Authorization` were sent |
| `payload-too-large` | 413 | A signed body over 12 MB |

**Retries:** sign every attempt afresh with a new timestamp and nonce. The old signature is spent, and that's the
point.

**Multipart uploads** are signed over the exact multipart bytes. Postman builds those after its scripts run, so its
upload request goes unsigned. Upload from code, or with a key that doesn't require signing.

## 4. Webhooks

Add an endpoint in the DF Console (Developers, Webhooks), or with `POST /api/cns-cmd/webhooks/endpoints`. It is told
of every change to your consents, within seconds. The messages are in
[df-webhooks.asyncapi.yaml](df-webhooks.asyncapi.yaml).

- **Verify each message** before acting on it. Signatures follow [Standard Webhooks](https://www.standardwebhooks.com),
  so its libraries work: `new Webhook(secret).verify(body, headers)`. By hand, compute HMAC-SHA256 over
  `webhook-id.webhook-timestamp.body` with the base64-decoded secret after `whsec_`. Compare its base64 with each
  `v1,` value in `webhook-signature`, and refuse timestamps more than 5 minutes off. Verify the raw body as received,
  before parsing it.
- **Answer 2xx within 10 seconds**, then do the work. Anything else is retried for about two days, then marked failed
  until you send it again.
- **De-duplicate on `webhook-id`.** A message can arrive more than once; a replay keeps its id.
- **Rotate** the secret from the console or `POST .../endpoints/{id}/rotate-secret`. For 24 hours both secrets sign
  each message, so deploy the new one in your own time.

```python
import base64, hashlib, hmac, time

def verify(secret: str, headers: dict, body: bytes) -> bool:
    msg_id, ts = headers["webhook-id"], headers["webhook-timestamp"]
    if abs(time.time() - int(ts)) > 300:
        return False
    key = base64.b64decode(secret.removeprefix("whsec_"))
    expected = base64.b64encode(hmac.new(key, f"{msg_id}.{ts}.".encode() + body, hashlib.sha256).digest()).decode()
    return any(hmac.compare_digest(expected, s.split(",", 1)[1]) for s in headers["webhook-signature"].split())
```

## 5. Keep each consent tied to your customer

A consent is the person's decision about **your** purpose, so your systems must be able to say which customer a decision
belongs to. The Consent Manager does not do that for you, on purpose: the read model and the webhooks carry **ids, not the
email address or mobile number** you asked, so a message that leaks, or an endpoint that logs it, tells nobody who the person
is. You hold the link, and the link is the request id.

### The ids

| Where | Field | Is |
|---|---|---|
| `POST /api/cns-cmd/cns-reqs` answers `201` | `id` | the request's id: **keep it** |
| `GET /api/cns-qry/cns-arts`, `.../{requestId}` | `cnsReqId` | the same id |
| every `consent.*` webhook, in `data` | `cnsReqId` | the same id |
| `consent.given` and later | `cnsArtId` | the consent itself, set once the person gives it |
| every webhook | `id` (and the `webhook-id` header) | the message: de-duplicate on it |

Note that the create call says `id` and everything after says `cnsReqId`. They are one value.

### What to keep, and when

1. **Before you ask**, note in your own database that you are about to ask customer X for purpose P (version V). Nothing stops
   two requests to the same person, and the platform does not de-duplicate them, so an unnoticed retry asks twice.
2. **As soon as the call answers `201`**, save the request `id` against that customer.
3. **As decisions arrive**, update that row from the webhook, keyed by `cnsReqId`.

| Keep | From | Why |
|---|---|---|
| your customer id | you | the link everything else hangs on |
| request id | `id`, then `cnsReqId` | the key |
| purpose code and version | what you asked for | what the person agreed to is *that version's* notice |
| state | `PENDING`, `ISSUED`, `DENIED`, `REVOKED`, `EXPIRED`, `NOTIFICATION_FAILED` | whether you may use the data |
| consent id | `cnsArtId` | to withdraw, check and reconcile later |
| data granted | `dCatCodes` in `consent.given`, or `grantedDCatCodes` when you read it back | use only these (the person may grant fewer than you asked) |
| valid until | `validUntil` | stop using the data after it |

Keep the person's email or mobile in your customer record, as you do today, not in the consent table.

### What each message means for your systems

| Message | State | Do |
|---|---|---|
| `consent.requested` | `PENDING` | nothing: confirms the person was asked |
| `consent.notification_failed` | `NOTIFICATION_FAILED` | the person was not reached: try another contact |
| `consent.given` | `ISSUED` | record `cnsArtId`, `dCatCodes`, `validUntil`; start the processing it allows |
| `consent.refused` | `DENIED` | record it; do not use the data or ask again for this purpose without a reason |
| `consent.modified` | `ISSUED` | narrow to the new `dCatCodes` (`change` says what) |
| `consent.renewed` | `ISSUED` | extend to the new `validUntil` |
| `consent.withdrawn` | `REVOKED` | stop at once, for this purpose |
| `consent.lapsed` | `EXPIRED` | stop; ask again if you still need it |
| `principal_request.received` | n/a | route the correction or complaint to the team that answers it |

A message for a request id you do not have was not raised by this system (another integration, or someone in the console):
acknowledge it with a 2xx and leave it alone.

### A worked example

SQLite and Python, with the signature check from section 4. The same shape works in any language and database.

```python
import base64, hashlib, hmac, json, sqlite3, time

db = sqlite3.connect(":memory:")
db.executescript("""
create table consent (
  request_id   text primary key,   -- the platform's id: `id` when you raise it, `cnsReqId` everywhere after
  customer_id  text not null,      -- YOUR customer
  purpose      text not null,
  purpose_ver  integer not null,
  state        text not null,      -- PENDING | ISSUED | DENIED | REVOKED | EXPIRED | NOTIFICATION_FAILED
  consent_id   text,               -- `cnsArtId`, once given
  categories   text,               -- what was granted
  valid_until  text,
  updated_at   text not null
);
create table seen (message_id text primary key);
""")


def record_request(customer_id: str, purpose: str, version: int, created: dict) -> None:
    """Call as soon as POST /api/cns-cmd/cns-reqs answers 201: `created` is its JSON body."""
    db.execute("insert into consent (request_id, customer_id, purpose, purpose_ver, state, updated_at) values (?,?,?,?,?,?)",
               (created["id"], customer_id, purpose, version, created.get("sts", "PENDING"), time.strftime("%Y-%m-%dT%H:%M:%SZ")))
    db.commit()


STATE = {"consent.given": "ISSUED", "consent.renewed": "ISSUED", "consent.modified": "ISSUED", "consent.refused": "DENIED",
         "consent.withdrawn": "REVOKED", "consent.lapsed": "EXPIRED", "consent.notification_failed": "NOTIFICATION_FAILED"}


def verify(secret: str, headers: dict, body: bytes) -> bool:
    msg_id, ts = headers["webhook-id"], headers["webhook-timestamp"]
    if abs(time.time() - int(ts)) > 300:
        return False
    key = base64.b64decode(secret.removeprefix("whsec_"))
    expected = base64.b64encode(hmac.new(key, f"{msg_id}.{ts}.".encode() + body, hashlib.sha256).digest()).decode()
    return any(hmac.compare_digest(expected, s.split(",", 1)[1]) for s in headers["webhook-signature"].split())


def on_webhook(secret: str, headers: dict, body: bytes) -> int:
    """Returns the HTTP status to answer with. Verify the raw body first, answer fast, and ignore a message seen before."""
    if not verify(secret, headers, body):
        return 400
    if db.execute("select 1 from seen where message_id = ?", (headers["webhook-id"],)).fetchone():
        return 200  # a redelivery
    msg = json.loads(body)
    d = msg.get("data", {})
    new_state = STATE.get(msg["type"])
    if new_state and "cnsReqId" in d:
        # the request id is the key to your customer; an id you did not raise is not yours to act on
        row = db.execute("select customer_id from consent where request_id = ?", (d["cnsReqId"],)).fetchone()
        if row is None:
            return 200  # not raised by this system (another integration, or the console): nothing to correlate
        db.execute("update consent set state = ?, consent_id = coalesce(?, consent_id), categories = coalesce(?, categories), "
                   "valid_until = coalesce(?, valid_until), updated_at = ? where request_id = ?",
                   (new_state, d.get("cnsArtId"), json.dumps(d["dCatCodes"]) if "dCatCodes" in d else None, d.get("validUntil"),
                    msg["occurredAt"], d["cnsReqId"]))
    db.execute("insert into seen (message_id) values (?)", (headers["webhook-id"],))
    db.commit()
    return 200
```

### Do not rely on the webhook alone

A webhook is the fast path, not the record. Your endpoint may be down, and a message is retried for about two days and then
marked failed until you send it again from the console. So also **reconcile**: on a schedule (hourly is plenty), read
`GET /api/cns-qry/cns-arts`, and for each row whose `lcySts` differs from yours, take the platform's value. After an outage,
that is how you catch up. Reconcile also finds the case where `POST` timed out and you do not know whether the request was
made: list your recent requests, match on purpose and time, and save the id of the one you find before asking again.

Check consent just before using data, not only when the webhook says so. `GET /api/cns-qry/cns-arts/{cnsArtId}/verify`
(with the consent token from `POST /api/cns-cmd/cns-arts/{cnsArtId}/token`) answers "is this still allowed right now, for
this purpose?", so a withdrawal takes effect at once even if its webhook is still on its way.
