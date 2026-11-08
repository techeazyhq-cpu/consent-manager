#!/usr/bin/env node
/*
 * Copyright 2026 Vasantha Kumar
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author Vasantha Kumar <vasantha.kumar@hotmail.com>
 */

// The AI ingestion guard (sdk/ai-ingestion-guard, ADR-069) against the real local stack: a throwaway principal gives three consents
// and a fiduciary's pipeline ingests data under them.
//   - data under a consent that allows the purpose is embedded; under a purpose the consent does not cover, it is not
//   - the principal withdraws one consent: the Consent Manager's webhook, verified by its signature, takes that consent's vectors out
//   - a forged webhook, and one with a bad signature, change nothing
//   - the principal withdraws another and its webhook is lost: reconcile takes its vectors out
//   - data offered under a withdrawn consent is not embedded, and the embedding model never sees its text
// Needs the keys file from setup-keys.mjs. It runs a web server for the webhook and registers it as an endpoint of Batch Test Co.
//
//   KEYS_FILE=/tmp/cm-keys.json node tests/security/e2e-ai-ingestion.mjs

import { createServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { GW, call, dpSignIn, loadKeys } from './lib/cm1.mjs';
import { ConsentClient, IngestionGuard, InMemoryVectorStore, SeenSet, verifyWebhook } from '../../sdk/ai-ingestion-guard/src/index.mjs';

const KEYS = loadKeys(process.env.KEYS_FILE ?? (() => { throw new Error('Set KEYS_FILE to the file setup-keys.mjs wrote'); })());
let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const until = async (test, tries = 30) => {
  for (let i = 0; i < tries; i++) {
    if (await test()) return true;
    await sleep(1000);
  }
  return false;
};
const dpApi = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};

// three consents given by one throwaway principal
const dp = `ingest-${randomBytes(3).toString('hex')}@ingest.example.test`;
const raised = [];
for (let i = 0; i < 3; i++) {
  const r = await call(KEYS.a, 'POST', '/api/cns-cmd/cns-reqs', { body: { extDpRefTyp: 'EMAIL', extDpRefVal: dp, prpsCode: 'ORDER_UPDATES', prpsVer: KEYS.prpsVerA } });
  raised.push(r.json.id);
}
const token = await dpSignIn(dp);
await dpApi(token, 'POST', '/api/cns-cmd/dp-links', { extDpRefTyp: 'EMAIL', extDpRefVal: dp });
const art = [];
for (const id of raised) art.push((await dpApi(token, 'POST', `/api/cns-cmd/cns-reqs/${id}/approve`)).json.id);
expect(art.length === 3 && art.every(Boolean), 'a principal has given three consents', art.map((a) => a?.slice(0, 8)).join(' '));

// the pipeline
const client = new ConsentClient({ gateway: GW, apiKey: KEYS.a.apiKey, signingSecret: KEYS.a.signingSecret });
const store = new InMemoryVectorStore();
const modelSaw = [];
const embed = async (text) => { modelSaw.push(text); return [text.length]; };
const guard = new IngestionGuard({ client, store, embed, purpose: 'ORDER_UPDATES' });

const first = await guard.ingest([{ id: 'doc-1', text: 'order one', cnsArtId: art[0] }, { id: 'doc-2', text: 'order two', cnsArtId: art[1] }, { id: 'doc-3', text: 'order three', cnsArtId: art[2] }]);
expect(first.ingested.length === 3 && first.skipped.length === 0 && first.deferred.length === 0, 'data under consents that allow the purpose is embedded', JSON.stringify(first));
const other = await new IngestionGuard({ client, store: new InMemoryVectorStore(), embed, purpose: 'MARKETING' }).ingest([{ id: 'doc-x', text: 'marketing text', cnsArtId: art[0] }]);
expect(other.ingested.length === 0 && other.skipped[0]?.reason, 'the same consent does not cover another purpose: nothing is embedded', other.skipped[0]?.reason);
expect(!modelSaw.includes('marketing text'), 'and the model never saw the text');

// the webhook receiver
const received = [];
const seen = new SeenSet();
let secret = null;
const hooks = createServer(async (req, res) => {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const rawBody = Buffer.concat(chunks).toString('utf8');
  try {
    const event = verifyWebhook({ headers: req.headers, rawBody, secrets: [secret] });
    if (!seen.first(event.webhookId)) return res.writeHead(200).end();
    received.push(event);
    // a lost webhook, for the reconcile check: this receiver ignores everything about the third consent
    if (event.data?.cnsArtId !== art[2]) await guard.onWebhook(event);
    res.writeHead(200).end();
  } catch (e) {
    received.push({ refused: e.message });
    res.writeHead(400).end();
  }
});
await new Promise((r) => hooks.listen(0, '0.0.0.0', r));
const port = hooks.address().port;
let endpointId;
try {
  const created = await call(KEYS.a, 'POST', '/api/cns-cmd/webhooks/endpoints', { body: { url: `http://host.docker.internal:${port}/hook`, description: 'e2e ai ingestion', types: ['consent.withdrawn', 'consent.lapsed', 'consent.modified'] } });
  expect(created.status === 201 && created.json.secret?.startsWith('whsec_'), 'the pipeline registers a webhook endpoint', `${created.status}`);
  secret = created.json.secret;
  endpointId = created.json.endpoint.id;

  // a withdrawal, delivered
  expect((await dpApi(token, 'POST', `/api/cns-cmd/cns-arts/${art[0]}/revoke`)).status === 200, 'the principal withdraws the first consent');
  const purged = await until(() => ![...store.items.values()].some((v) => v.metadata.cnsArtId === art[0]));
  expect(purged, 'the verified webhook takes that consent\'s vectors out', `${received.filter((e) => e.type).map((e) => e.type).join(',')}`);
  expect(store.items.has('doc-2') && store.items.has('doc-3'), 'and nobody else\'s');

  // forged deliveries
  const body = JSON.stringify({ id: 'x', type: 'consent.withdrawn', occurredAt: new Date().toISOString(), data: { cnsArtId: art[1] } });
  const ts = String(Math.floor(Date.now() / 1000));
  const forged = await fetch(`http://localhost:${port}/hook`, { method: 'POST', headers: { 'webhook-id': 'forged', 'webhook-timestamp': ts, 'webhook-signature': 'v1,AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=' }, body });
  const unsigned = await fetch(`http://localhost:${port}/hook`, { method: 'POST', body });
  expect(forged.status === 400 && unsigned.status === 400 && store.items.has('doc-2'), 'a forged webhook and an unsigned one are refused and delete nothing', `${forged.status} ${unsigned.status}`);

  // a withdrawal whose webhook is lost
  expect((await dpApi(token, 'POST', `/api/cns-cmd/cns-arts/${art[2]}/revoke`)).status === 200, 'the principal withdraws the third consent, and its webhook is lost');
  await until(() => received.some((e) => e.type === 'consent.withdrawn' && e.data?.cnsArtId === art[2]));
  expect(store.items.has('doc-3'), 'its vectors are still there: nothing acted on the webhook');
  const summary = await guard.reconcile();
  expect(!store.items.has('doc-3') && summary.purged >= 1, 'reconcile takes them out', JSON.stringify(summary));
  expect(store.items.has('doc-2'), 'and leaves the consent still in force');

  // after withdrawal
  const seenBefore = modelSaw.length;
  const again = await guard.ingest([{ id: 'doc-4', text: 'late text for a withdrawn consent', cnsArtId: art[0] }]);
  expect(again.ingested.length === 0 && again.skipped[0]?.reason === 'consent-not-in-force', 'data offered under a withdrawn consent is not embedded', again.skipped[0]?.reason);
  expect(modelSaw.length === seenBefore && !modelSaw.some((t) => t.includes('late text')), 'and the model never sees its text');
} finally {
  if (endpointId) await call(KEYS.a, 'DELETE', `/api/cns-cmd/webhooks/endpoints/${endpointId}`);
  hooks.close();
}

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
