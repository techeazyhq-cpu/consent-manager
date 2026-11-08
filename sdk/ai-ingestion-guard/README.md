# AI ingestion guard

The check a fiduciary makes before personal data becomes embeddings, and the clean-up when the person's consent ends
([ADR-069](../../docs/adr/adr-069-ai-ingestion-guard.md)). A reference library: zero dependencies, Node 20+, one file
([`src/index.mjs`](src/index.mjs)) to read, copy or port. It uses only the Consent Manager's public API, so the same pattern works in
any language.

## Why

Once a person's text is in a vector index it is hard to find again, and hard to take out: an embedding does not say whose it is.
Consent therefore has to be checked **before** the text is embedded, and the vector has to carry which consent it was made under, so
it can be removed when that consent ends. Checking at query time is too late: the data has already been processed.

## Use

```js
import { ConsentClient, IngestionGuard, verifyWebhook, SeenSet } from './src/index.mjs';

const client = new ConsentClient({ gateway: 'https://api.example.com', apiKey, signingSecret });
const guard = new IngestionGuard({ client, store, embed, purpose: 'SUPPORT_ASSISTANT' });

// 1. ingest: only what consent allows is embedded
const { ingested, skipped, deferred } = await guard.ingest(tickets.map((t) => ({ id: t.id, text: t.body, cnsArtId: t.consentId, categories: ['EMAIL'] })));
//   skipped:  no consent, or it does not allow this purpose: do not retry
//   deferred: the Consent Manager could not be asked right now: retry after retryAfterSeconds (this is NOT a refusal)

// 2. the webhook: a withdrawn or lapsed consent takes its vectors out at once
const seen = new SeenSet();
app.post('/consent-webhook', rawBodyParser, async (req, res) => {
  const event = verifyWebhook({ headers: req.headers, rawBody: req.rawBody, secrets: [process.env.CM_WEBHOOK_SECRET] });
  if (seen.first(event.webhookId)) await guard.onWebhook(event);   // delivery is at least once
  res.sendStatus(200);
});

// 3. reconcile, on a schedule (hourly): catches a webhook that never arrived
await guard.reconcile();
```

## What it guarantees, and what it does not

- **The embedding model never sees text that consent does not allow.** `embed` is called only after the consent has been verified
  for the purpose, and again before storing if embedding took longer than `recheckAfterMs` (a person may withdraw in between).
- **"Could not check" is never "withdrawn" and never "allowed".** A 503 or a network failure defers the item. During `reconcile`, a
  consent that cannot be checked keeps its vectors until it can.
- **Every vector says which consent it was made under** (`metadata.cnsArtId`, with the purpose and categories), which is what makes
  removal possible.
- **A withdrawn data category removes the vectors of that category**, if the store supports `deleteByConsentAndCategory`; a store
  that cannot tell categories apart removes the whole consent's vectors, the safe side.
- **Webhooks are verified** as Standard Webhooks (HMAC-SHA256, constant-time comparison, a 5-minute window, two signatures while a
  secret is rotating) and de-duplicated on `webhook-id`. A forged or unsigned one deletes nothing.

It does **not**: remove what a model already *learned* from data (fine-tuning is not an index; do not fine-tune on data whose
consent can be withdrawn), scrub caches, logs or derived summaries you keep elsewhere, or decide the lawful basis for data that is
not consent-based (that is yours, and this guard sends it nowhere: an item with no `cnsArtId` is skipped).

## The store

A vector store implements four methods; [`InMemoryVectorStore`](src/index.mjs) is the reference. For a real one:

| Method | What |
|---|---|
| `upsert({ id, vector, metadata })` | keeps the vector with `metadata.cnsArtId`, `purpose`, `categories`, `ingestedAt` |
| `deleteByConsent(cnsArtId)` | removes every vector made under that consent; returns how many |
| `consentIds()` | the distinct consents the store holds vectors for (for `reconcile`) |
| `deleteByConsentAndCategory(cnsArtId, category)` | optional: removes only that category's vectors |

In pgvector this is a column and an index on `cns_art_id`; in Qdrant or Pinecone, a payload field or metadata filter on it.

## Tests

```bash
node --test sdk/ai-ingestion-guard/test/*.test.mjs                 # unit tests, no network
KEYS_FILE=/tmp/cm-keys.json node tests/security/e2e-ai-ingestion.mjs   # against the local stack
```
