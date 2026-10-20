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
