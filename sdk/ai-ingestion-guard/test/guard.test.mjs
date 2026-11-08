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

import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { test } from 'node:test';
import { ConsentClient, Decision, InMemoryVectorStore, IngestionGuard, SeenSet, signRequest, verifyWebhook } from '../src/index.mjs';

/** A client whose answers are written down per consent: ALLOW, DENY:<reason>, or UNAVAILABLE. */
const fakeClient = (answers) => ({
  calls: [],
  async verify(id, purpose) {
    this.calls.push([id, purpose]);
    const a = typeof answers[id] === 'function' ? answers[id]() : answers[id];
    if (a === 'ALLOW') return { decision: Decision.ALLOW };
    if (a === 'UNAVAILABLE') return { decision: Decision.UNAVAILABLE, retryAfterSeconds: 7 };
    return { decision: Decision.DENY, reason: String(a).split(':')[1] ?? 'not-allowed' };
  },
});
const embedder = () => {
  const seen = [];
  return { seen, embed: async (text) => { seen.push(text); return [text.length]; } };
};

test('only an item whose consent allows the purpose is embedded, and the text of the others is never read', async () => {
  const e = embedder();
  const store = new InMemoryVectorStore();
  const client = fakeClient({ a: 'ALLOW', b: 'DENY:consent-withdrawn', c: 'UNAVAILABLE' });
  const guard = new IngestionGuard({ client, store, embed: e.embed, purpose: 'ASSIST' });

  const out = await guard.ingest([
    { id: '1', text: 'allowed text', cnsArtId: 'a', categories: ['EMAIL'] },
    { id: '2', text: 'withdrawn text', cnsArtId: 'b' },
    { id: '3', text: 'unknown text', cnsArtId: 'c' },
    { id: '4', text: 'no reference text' },
  ]);

  assert.deepEqual(out.ingested, ['1']);
  assert.deepEqual(out.skipped, [{ id: '2', reason: 'consent-withdrawn' }, { id: '4', reason: 'no-consent-reference' }]);
  assert.deepEqual(out.deferred, [{ id: '3', retryAfterSeconds: 7 }]);
  assert.deepEqual(e.seen, ['allowed text'], 'the embedding model saw nothing but what consent allowed');
  assert.deepEqual([...store.items.keys()], ['1']);
  assert.deepEqual(client.calls.map((c) => c[1]), ['ASSIST', 'ASSIST', 'ASSIST'], 'every check names the purpose');
  const meta = store.items.get('1').metadata;
  assert.equal(meta.cnsArtId, 'a');
  assert.equal(meta.purpose, 'ASSIST');
  assert.deepEqual(meta.categories, ['EMAIL']);
});

test('an item not checkable now is deferred, not skipped: a platform outage is not a withdrawal', async () => {
  const guard = new IngestionGuard({ client: fakeClient({ a: 'UNAVAILABLE' }), store: new InMemoryVectorStore(), embed: async () => [1], purpose: 'P' });

  const out = await guard.ingest([{ id: '1', text: 't', cnsArtId: 'a' }]);

  assert.equal(out.skipped.length, 0);
  assert.equal(out.deferred.length, 1);
});

test('a consent withdrawn while a slow embedding was computed is checked again before storing', async () => {
  let t = 0;
  let calls = 0;
  const client = fakeClient({ a: () => (++calls === 1 ? 'ALLOW' : 'DENY:consent-withdrawn') });
  const store = new InMemoryVectorStore();
  const guard = new IngestionGuard({ client, store, purpose: 'P', recheckAfterMs: 5000, now: () => t, embed: async () => { t += 6000; return [1]; } });

  const out = await guard.ingest([{ id: '1', text: 't', cnsArtId: 'a' }]);

  assert.deepEqual(out.skipped, [{ id: '1', reason: 'consent-withdrawn' }]);
  assert.equal(store.items.size, 0);
  assert.equal(calls, 2);
});

test('a fast embedding is not checked a second time', async () => {
  const client = fakeClient({ a: 'ALLOW' });
  const guard = new IngestionGuard({ client, store: new InMemoryVectorStore(), embed: async () => [1], purpose: 'P' });

  await guard.ingest([{ id: '1', text: 't', cnsArtId: 'a' }]);

  assert.equal(client.calls.length, 1);
});

test('a withdrawn or lapsed consent takes all its vectors out, and nobody else\'s', async () => {
  const store = new InMemoryVectorStore();
  const guard = new IngestionGuard({ client: fakeClient({ a: 'ALLOW', b: 'ALLOW' }), store, embed: async () => [1], purpose: 'P' });
  await guard.ingest([{ id: '1', text: 'x', cnsArtId: 'a' }, { id: '2', text: 'y', cnsArtId: 'a' }, { id: '3', text: 'z', cnsArtId: 'b' }]);

  assert.deepEqual(await guard.onWebhook({ type: 'consent.withdrawn', data: { cnsArtId: 'a' } }), { action: 'purged', deleted: 2 });
  assert.deepEqual([...store.items.keys()], ['3']);
  assert.deepEqual(await guard.onWebhook({ type: 'consent.lapsed', data: { cnsArtId: 'b' } }), { action: 'purged', deleted: 1 });
  assert.equal(store.items.size, 0);
});

test('a withdrawn data category takes out only the vectors of that category', async () => {
  const store = new InMemoryVectorStore();
  const guard = new IngestionGuard({ client: fakeClient({ a: 'ALLOW' }), store, embed: async () => [1], purpose: 'P' });
  await guard.ingest([{ id: '1', text: 'x', cnsArtId: 'a', categories: ['EMAIL'] }, { id: '2', text: 'y', cnsArtId: 'a', categories: ['PHONE'] }]);

  const done = await guard.onWebhook({ type: 'consent.modified', data: { cnsArtId: 'a', change: 'CATEGORY_WITHDRAWN', dCatWithdrawn: 'EMAIL' } });

  assert.deepEqual(done, { action: 'purged-category', deleted: 1 });
  assert.deepEqual([...store.items.keys()], ['2']);
});

test('a store that cannot tell categories apart loses the whole consent\'s vectors: the safe side', async () => {
  const items = new Map([['1', { id: '1', metadata: { cnsArtId: 'a', categories: ['EMAIL'] } }]]);
  const store = { upsert: async () => {}, consentIds: async () => ['a'], deleteByConsent: async () => { const n = items.size; items.clear(); return n; } };
  const guard = new IngestionGuard({ client: fakeClient({}), store, embed: async () => [1], purpose: 'P' });

  assert.deepEqual(await guard.onWebhook({ type: 'consent.modified', data: { cnsArtId: 'a', change: 'CATEGORY_WITHDRAWN', dCatWithdrawn: 'EMAIL' } }), { action: 'purged', deleted: 1 });
});

test('other events, a shortened consent and an event with no consent change nothing', async () => {
  const store = new InMemoryVectorStore();
  const guard = new IngestionGuard({ client: fakeClient({ a: 'ALLOW' }), store, embed: async () => [1], purpose: 'P' });
  await guard.ingest([{ id: '1', text: 'x', cnsArtId: 'a' }]);

  for (const e of [{ type: 'consent.given', data: { cnsArtId: 'a' } }, { type: 'consent.renewed', data: { cnsArtId: 'a' } },
    { type: 'consent.modified', data: { cnsArtId: 'a', change: 'SHORTENED' } }, { type: 'webhook.test', data: {} }, { type: 'consent.withdrawn', data: {} }]) {
    assert.equal((await guard.onWebhook(e)).deleted, 0, e.type);
  }
  assert.equal(store.items.size, 1);
});

test('reconcile takes out what is no longer allowed, keeps what is, and leaves what could not be checked', async () => {
  const store = new InMemoryVectorStore();
  const answers = { ok: 'ALLOW', gone: 'ALLOW', down: 'ALLOW' };
  const guard = new IngestionGuard({ client: fakeClient(answers), store, embed: async () => [1], purpose: 'P' });
  await guard.ingest([{ id: '1', text: 'x', cnsArtId: 'ok' }, { id: '2', text: 'y', cnsArtId: 'gone' }, { id: '3', text: 'z', cnsArtId: 'down' }]);
  answers.gone = 'DENY:consent-withdrawn';
  answers.down = 'UNAVAILABLE';

  const summary = await guard.reconcile();

  assert.deepEqual(summary, { checked: 3, purged: 1, deleted: 1, unavailable: 1 });
  assert.deepEqual([...store.items.keys()].sort(), ['1', '3'], 'the unchecked consent\'s vector stays: could not check is not withdrawn');
});

// ---------------------------------------------------------------------------------------------------- webhooks

const SECRET = `whsec_${Buffer.from('a-test-secret-of-some-length').toString('base64')}`;
const sign = (id, ts, body, secret = SECRET) => `v1,${createHmac('sha256', Buffer.from(secret.replace(/^whsec_/, ''), 'base64')).update(`${id}.${ts}.${body}`).digest('base64')}`;
const NOW = Date.parse('2026-11-08T10:00:00Z');
const ts = String(NOW / 1000);
const body = JSON.stringify({ id: 'm1', type: 'consent.withdrawn', occurredAt: '2026-11-08T10:00:00Z', data: { cnsArtId: 'a' } });

test('a webhook is accepted when its signature verifies, and carries its id to de-duplicate on', () => {
  const event = verifyWebhook({ headers: { 'Webhook-Id': 'm1', 'Webhook-Timestamp': ts, 'Webhook-Signature': sign('m1', ts, body) }, rawBody: body, secrets: [SECRET], now: NOW });

  assert.equal(event.type, 'consent.withdrawn');
  assert.equal(event.webhookId, 'm1');
});

test('either signature is accepted while a secret is rotating, and a wrong or missing one is refused', () => {
  const other = `whsec_${Buffer.from('another-secret').toString('base64')}`;
  const both = `${sign('m1', ts, body, other)} ${sign('m1', ts, body)}`;
  assert.ok(verifyWebhook({ headers: { 'webhook-id': 'm1', 'webhook-timestamp': ts, 'webhook-signature': both }, rawBody: body, secrets: [SECRET], now: NOW }));

  for (const sig of [sign('m1', ts, body, other), 'v1,AAAA', '', 'v2,whatever']) {
    assert.throws(() => verifyWebhook({ headers: { 'webhook-id': 'm1', 'webhook-timestamp': ts, 'webhook-signature': sig }, rawBody: body, secrets: [SECRET], now: NOW }));
  }
  assert.throws(() => verifyWebhook({ headers: {}, rawBody: body, secrets: [SECRET], now: NOW }), /not a signed webhook/);
});

test('a body altered after signing, or a replay from long ago, is refused', () => {
  const headers = { 'webhook-id': 'm1', 'webhook-timestamp': ts, 'webhook-signature': sign('m1', ts, body) };

  assert.throws(() => verifyWebhook({ headers, rawBody: body.replace('withdrawn', 'given'), secrets: [SECRET], now: NOW }), /does not verify/);
  assert.throws(() => verifyWebhook({ headers, rawBody: body, secrets: [SECRET], now: NOW + 6 * 60_000 }), /too far/);
  assert.ok(verifyWebhook({ headers, rawBody: body, secrets: [SECRET], now: NOW + 4 * 60_000 }));
});

test('a webhook id is seen as new once', () => {
  const seen = new SeenSet(2);
  assert.equal(seen.first('a'), true);
  assert.equal(seen.first('a'), false);
  seen.first('b');
  seen.first('c');
  assert.equal(seen.first('a'), true, 'the oldest is forgotten past the limit');
});

// ---------------------------------------------------------------------------------------------------- the client

const response = (status, json, headers = {}) => ({ status, ok: status >= 200 && status < 300, json: async () => json, headers: { get: (k) => headers[k.toLowerCase()] } });
const clientWith = (handler, now = () => Date.parse('2026-11-08T10:00:00Z')) => {
  const sent = [];
  const fetchImpl = async (url, init) => { sent.push({ url: new URL(url), init }); return handler(new URL(url), init); };
  return { sent, client: new ConsentClient({ gateway: 'http://gw/', apiKey: 'cmk_x', signingSecret: 'cms_y', fetchImpl, now }) };
};
const tokenAnd = (verify) => (url) => (url.pathname.endsWith('/token') ? response(200, { cnsTkn: 'tkn', exp: '2026-11-08T10:05:00Z' }) : verify(url));

test('the client fetches a signed token, then verifies with it, for the purpose asked', async () => {
  const { client, sent } = clientWith(tokenAnd(() => response(200, { result: 'ALLOW' })));

  assert.deepEqual(await client.verify('art-1', 'ASSIST'), { decision: Decision.ALLOW });

  assert.equal(sent[0].init.method, 'POST');
  assert.ok(sent[0].init.headers['X-Signature'] && sent[0].init.headers['X-API-Key'] === 'cmk_x');
  assert.equal(sent[1].url.pathname, '/api/cns-qry/cns-arts/art-1/verify');
  assert.equal(sent[1].url.searchParams.get('purpose'), 'ASSIST');
  assert.equal(sent[1].init.headers['X-Cns-Tkn'], 'tkn');
});

test('the token is reused until shortly before it expires', async () => {
  let now = Date.parse('2026-11-08T10:00:00Z');
  const { client, sent } = clientWith(tokenAnd(() => response(200, {})), () => now);

  await client.verify('a', 'P');
  await client.verify('a', 'P');
  assert.equal(sent.filter((s) => s.url.pathname.endsWith('/token')).length, 1);
  now += 4.6 * 60_000;
  await client.verify('a', 'P');
  assert.equal(sent.filter((s) => s.url.pathname.endsWith('/token')).length, 2);
});

test('withdrawn, wrong purpose and no such consent are DENY; a 503 and a network failure are UNAVAILABLE, never DENY', async () => {
  assert.deepEqual(await clientWith(tokenAnd(() => response(403, { code: 'consent-not-in-force' }))).client.verify('a', 'P'), { decision: Decision.DENY, reason: 'consent-not-in-force' });
  assert.deepEqual(await clientWith(() => response(409, {})).client.verify('a', 'P'), { decision: Decision.DENY, reason: 'consent-not-in-force' });
  assert.equal((await clientWith(() => response(404, {})).client.verify('a', 'P')).decision, Decision.DENY);

  const down = await clientWith(tokenAnd(() => response(503, {}, { 'retry-after': '12' }))).client.verify('a', 'P');
  assert.deepEqual(down, { decision: Decision.UNAVAILABLE, reason: 'consent-check-unavailable', retryAfterSeconds: 12 });
  assert.equal((await clientWith(() => { throw new TypeError('fetch failed'); }).client.verify('a', 'P')).decision, Decision.UNAVAILABLE);
  assert.equal((await clientWith(() => response(500, {})).client.verify('a', 'P')).decision, Decision.UNAVAILABLE);
});

test('a refused key is an error to fix, not a decision about the person', async () => {
  await assert.rejects(clientWith(() => response(401, {})).client.verify('a', 'P'), /refused this key/);
  await assert.rejects(clientWith(tokenAnd(() => response(401, {}))).client.verify('a', 'P'), /refused this key/);
});

test('trailing slashes on the gateway address are ignored, however many', async () => {
  const sent = [];
  const client = new ConsentClient({ gateway: `http://gw${'/'.repeat(50_000)}`, apiKey: 'k', signingSecret: 's', fetchImpl: async (url) => { sent.push(String(url)); return response(409, {}); } });

  await client.verify('a', 'P');

  assert.equal(sent[0], 'http://gw/api/cns-cmd/cns-arts/a/token');
});

test('a client needs its three settings', () => {
  assert.throws(() => new ConsentClient({ gateway: 'http://gw' }), /needed/);
});

test('a request is signed over its method, path, query and body, as the platform checks it', () => {
  const h = signRequest('secret', 'get', '/p', 'a=1', '', 1_700_000_000_000);
  const text = ['CM1-HMAC-SHA256', '1700000000', 'NONCE', 'GET', '/p', 'a=1', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'].join('\n');
  const expected = createHmac('sha256', 'secret').update(text.replace('NONCE', h['X-Signature-Nonce'])).digest('hex');

  assert.equal(h['X-Signature'], expected);
  assert.equal(h['X-Signature-Timestamp'], '1700000000');
});
