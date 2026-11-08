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
import { generateKeyPairSync, sign } from 'node:crypto';
import { createServer } from 'node:http';
import { deflateSync } from 'node:zlib';
import { test } from 'node:test';
import { Decision, Sidecar, cachedJwks, statusBit, verifyStatusList, verifyToken } from '../src/sidecar.mjs';
import { handler } from '../src/server.mjs';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const jwks = { keys: [{ ...publicKey.export({ format: 'jwk' }), kid: 'k1', alg: 'RS256', use: 'sig' }] };
const NOW = Date.parse('2026-11-08T12:00:00Z');

const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
function token(over = {}, { key = privateKey, kid = 'k1', alg = 'RS256' } = {}) {
  const claims = { iss: 'cm-cns-cmd-svc', cns_art_id: 'art-1', fiduciary_id: 'df-1', purpose_code: 'ORDER_UPDATES', access_type: 'ALLOW', exp: NOW / 1000 + 600, ...over };
  const head = b64({ alg, kid });
  const body = b64(claims);
  return `${head}.${body}.${sign('sha256', Buffer.from(`${head}.${body}`), key).toString('base64url')}`;
}
const expecting = { cnsArtId: 'art-1', purpose: 'ORDER_UPDATES', fiduciaryId: 'df-1', now: NOW };

test('a token the Consent Manager signed, for this consent, fiduciary and purpose, verifies offline', () => {
  const r = verifyToken(token(), jwks, expecting);
  assert.equal(r.ok, true);
  assert.equal(r.claims.cns_art_id, 'art-1');
});

test('a token is refused when it is forged, expired, for something else, or not an ALLOW', () => {
  const stranger = generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey;
  const cases = [
    ['token-signature', token({}, { key: stranger })],
    ['token-key-unknown', token({}, { kid: 'other' })],
    ['token-expired', token({ exp: NOW / 1000 - 1 })],
    ['token-other-consent', token({ cns_art_id: 'art-2' })],
    ['token-other-purpose', token({ purpose_code: 'MARKETING' })],
    ['token-other-fiduciary', token({ fiduciary_id: 'df-2' })],
    ['token-not-allow', token({ access_type: 'DENY' })],
    ['token-issuer', token({ iss: 'someone-else' })],
    ['token-algorithm', token({}, { alg: 'none' })],
    ['token-malformed', 'not.a-token'],
  ];
  for (const [reason, t] of cases) assert.equal(verifyToken(t, jwks, expecting).reason, reason, reason);
});

/** A client whose live answer can be changed between calls, and which hands back a real token. */
function fakeClient(initial) {
  const c = { answer: initial, calls: 0, async verify() { c.calls++; return c.answer; }, async token() { return token(); } };
  return c;
}
const clock = () => { const c = { t: NOW, now: () => c.t, advance: (s) => { c.t += s * 1000; } }; return c; };
const live = (decision, extra = {}) => ({ decision, ...extra });

test('a live ALLOW is reused for the cache time and then asked again', async () => {
  const c = clock();
  const client = fakeClient(live(Decision.ALLOW));
  const s = new Sidecar({ client, jwks: async () => jwks, cacheSeconds: 5, now: c.now });

  assert.deepEqual(await s.check('art-1', 'ORDER_UPDATES'), { decision: 'ALLOW', basis: 'live' });
  assert.equal((await s.check('art-1', 'ORDER_UPDATES')).basis, 'cache');
  assert.equal(client.calls, 1);
  c.advance(6);
  await s.check('art-1', 'ORDER_UPDATES');
  assert.equal(client.calls, 2);
});

test('a withdrawal is seen as soon as the cache time ends, and a DENY says why', async () => {
  const c = clock();
  const client = fakeClient(live(Decision.ALLOW));
  const s = new Sidecar({ client, jwks: async () => jwks, cacheSeconds: 5, now: c.now });
  await s.check('art-1', 'ORDER_UPDATES');
  client.answer = live(Decision.DENY, { reason: 'consent-withdrawn' });
  c.advance(6);
  assert.deepEqual(await s.check('art-1', 'ORDER_UPDATES'), { decision: 'DENY', reason: 'consent-withdrawn', basis: 'live' });
});

test('with grace off an unreachable Consent Manager is UNAVAILABLE, never a yes', async () => {
  const c = clock();
  const client = fakeClient(live(Decision.ALLOW));
  const s = new Sidecar({ client, jwks: async () => jwks, cacheSeconds: 0, now: c.now });
  await s.check('art-1', 'ORDER_UPDATES');
  client.answer = live(Decision.UNAVAILABLE, { retryAfterSeconds: 7 });
  const r = await s.check('art-1', 'ORDER_UPDATES');
  assert.equal(r.decision, 'UNAVAILABLE');
  assert.equal(r.retryAfterSeconds, 7);
});

test('with grace on a recent ALLOW is bridged by the verified token, and says until when', async () => {
  const c = clock();
  const client = fakeClient(live(Decision.ALLOW));
  const s = new Sidecar({ client, jwks: async () => jwks, cacheSeconds: 0, graceSeconds: 120, fiduciaryId: 'df-1', now: c.now });
  await s.check('art-1', 'ORDER_UPDATES');
  client.answer = live(Decision.UNAVAILABLE);
  c.advance(60);

  const r = await s.check('art-1', 'ORDER_UPDATES');
  assert.equal(r.decision, 'ALLOW');
  assert.equal(r.basis, 'offline-token');
  assert.equal(r.staleUntil, new Date(NOW + 120_000).toISOString());
});

test('grace never outlasts its bound, the token, a purpose excluded from it, or a consent never seen allowed', async () => {
  const c = clock();
  const client = fakeClient(live(Decision.ALLOW));
  const s = new Sidecar({ client, jwks: async () => jwks, cacheSeconds: 0, graceSeconds: 120, noGracePurposes: ['HEALTH'], now: c.now });
  await s.check('art-1', 'ORDER_UPDATES');
  client.answer = live(Decision.UNAVAILABLE);

  c.advance(121);
  assert.equal((await s.check('art-1', 'ORDER_UPDATES')).decision, 'UNAVAILABLE', 'past the grace bound');
  assert.equal((await s.check('art-9', 'ORDER_UPDATES')).decision, 'UNAVAILABLE', 'never seen allowed');
  assert.equal((await s.check('art-1', 'HEALTH')).decision, 'UNAVAILABLE', 'excluded purpose');

  const short = new Sidecar({ client: { ...fakeClient(live(Decision.ALLOW)), async token() { return token({ exp: NOW / 1000 + 30 }); } }, jwks: async () => jwks, cacheSeconds: 0, graceSeconds: 600, now: c.now });
  c.t = NOW;
  await short.check('art-1', 'ORDER_UPDATES');
  short.client.answer = live(Decision.UNAVAILABLE);
  short.client.verify = async () => live(Decision.UNAVAILABLE);
  c.advance(31);
  assert.equal((await short.check('art-1', 'ORDER_UPDATES')).decision, 'UNAVAILABLE', 'the token has expired');
});

test('a DENY ends any grace for that consent', async () => {
  const c = clock();
  const client = fakeClient(live(Decision.ALLOW));
  const s = new Sidecar({ client, jwks: async () => jwks, cacheSeconds: 0, graceSeconds: 120, now: c.now });
  await s.check('art-1', 'ORDER_UPDATES');
  client.answer = live(Decision.DENY, { reason: 'consent-withdrawn' });
  await s.check('art-1', 'ORDER_UPDATES');
  client.answer = live(Decision.UNAVAILABLE);
  assert.equal((await s.check('art-1', 'ORDER_UPDATES')).decision, 'UNAVAILABLE');
});

test('without the published keys grace cannot be used', async () => {
  const c = clock();
  const client = fakeClient(live(Decision.ALLOW));
  const s = new Sidecar({ client, jwks: async () => { throw new Error('down'); }, cacheSeconds: 0, graceSeconds: 120, now: c.now });
  await s.check('art-1', 'ORDER_UPDATES');
  client.answer = live(Decision.UNAVAILABLE);
  assert.equal((await s.check('art-1', 'ORDER_UPDATES')).decision, 'UNAVAILABLE');
});

test('the published keys are kept, and the last good ones are served when a refresh fails', async () => {
  let t = NOW;
  let up = true;
  let fetches = 0;
  const fetchImpl = async () => { fetches++; if (!up) throw new TypeError('down'); return { ok: true, json: async () => jwks }; };
  const get = cachedJwks({ gateway: 'https://api.example.com///', fetchImpl, now: () => t, ttlSeconds: 60 });
  assert.deepEqual(await get(), jwks);
  await get();
  assert.equal(fetches, 1);
  t += 61_000;
  up = false;
  assert.deepEqual(await get(), jwks);
  assert.equal(fetches, 2);
  await assert.rejects(cachedJwks({ gateway: 'https://x', fetchImpl: async () => { throw new TypeError('down'); } })());
});

test('a refused key is a fault to fix, reported as UNAVAILABLE and never a yes, and does not stop the sidecar', async () => {
  const faults = [];
  const client = { async verify() { throw new Error('the Consent Manager refused this key (401)'); }, async token() { return null; } };
  const s = new Sidecar({ client, jwks: async () => jwks, graceSeconds: 120, onFault: (e) => faults.push(e.message) });
  const r = await s.check('art-1', 'ORDER_UPDATES');
  assert.equal(r.decision, 'UNAVAILABLE');
  assert.equal(r.reason, 'sidecar-fault');
  assert.deepEqual(faults, ['the Consent Manager refused this key (401)']);
});

test('over HTTP: 200 for ALLOW and DENY, 503 with Retry-After for UNAVAILABLE, 400 for rubbish', async () => {
  const client = fakeClient(live(Decision.ALLOW));
  const s = new Sidecar({ client, jwks: async () => jwks, cacheSeconds: 0 });
  const server = createServer(handler(s));
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  const url = `http://127.0.0.1:${server.address().port}`;
  const post = (body) => fetch(`${url}/v1/check`, { method: 'POST', body });
  try {
    assert.equal((await post(JSON.stringify({ cnsArtId: 'art-1', purpose: 'ORDER_UPDATES' }))).status, 200);
    client.answer = live(Decision.DENY, { reason: 'consent-withdrawn' });
    const denied = await post(JSON.stringify({ cnsArtId: 'art-1', purpose: 'ORDER_UPDATES' }));
    assert.equal(denied.status, 200);
    assert.equal((await denied.json()).decision, 'DENY');
    client.answer = live(Decision.UNAVAILABLE, { retryAfterSeconds: 9 });
    const down = await post(JSON.stringify({ cnsArtId: 'art-1', purpose: 'ORDER_UPDATES' }));
    assert.equal(down.status, 503);
    assert.equal(down.headers.get('retry-after'), '9');
    assert.equal((await post('{not json')).status, 400);
    assert.equal((await post(JSON.stringify({ purpose: 'ORDER_UPDATES' }))).status, 200, 'a missing consent id is a DENY, not a crash');
    assert.equal((await fetch(`${url}/healthz`)).status, 200);
    assert.equal((await fetch(`${url}/nope`)).status, 404);
  } finally {
    server.close();
  }
});

// ---- list mode (ADR-077)

const LIST_URI = 'https://api.example.com/api/cns-cmd/status-lists/df-1';

/** A consent token that names its place in the fiduciary's status list. */
const listedToken = (idx, over = {}) => token({ status: { status_list: { idx, uri: LIST_URI } }, ...over });

/** A status list token: `ended` are the indexes whose bit is set. */
function statusList({ ended = [], uri = LIST_URI, exp = NOW / 1000 + 60, key = privateKey, typ = 'statuslist+jwt', size = 1024 } = {}) {
  const bits = Buffer.alloc(size / 8);
  for (const i of ended) bits[Math.floor(i / 8)] |= 1 << (i % 8);
  const head = b64({ alg: 'RS256', kid: 'k1', typ });
  const body = b64({ iss: 'cm-cns-cmd-svc', sub: uri, iat: NOW / 1000, exp, ttl: 60, status_list: { bits: 1, lst: deflateSync(bits).toString('base64url') } });
  return `${head}.${body}.${sign('sha256', Buffer.from(`${head}.${body}`), key).toString('base64url')}`;
}

test('a status list verifies, and its bits read least significant first', () => {
  const r = verifyStatusList(statusList({ ended: [0, 9] }), jwks, { uri: LIST_URI, now: NOW });
  assert.equal(r.ok, true);
  assert.equal(statusBit(r.bits, 0), true);
  assert.equal(statusBit(r.bits, 1), false);
  assert.equal(statusBit(r.bits, 9), true);
  assert.equal(statusBit(r.bits, 10), false);
  assert.equal(statusBit(r.bits, 1024), null, 'an index outside the list is not an answer');
});

test('a status list is refused when forged, expired, another list, or the wrong type', () => {
  const stranger = generateKeyPairSync('rsa', { modulusLength: 2048 }).privateKey;
  const cases = [
    ['status-list-signature', statusList({ key: stranger })],
    ['status-list-expired', statusList({ exp: NOW / 1000 - 1 })],
    ['status-list-other', statusList({ uri: 'https://elsewhere.example/list' })],
    ['status-list-type', statusList({ typ: 'JWT' })],
    ['status-list-malformed', 'nope'],
  ];
  for (const [reason, jwt] of cases) assert.equal(verifyStatusList(jwt, jwks, { uri: LIST_URI, now: NOW }).reason, reason, reason);
});

function listSidecar({ client, lists, now, origin = 'https://api.example.com' }) {
  const fetched = [];
  const s = new Sidecar({
    client, jwks: async () => jwks, mode: 'list', listOrigin: origin, now,
    fetchList: async (uri) => { fetched.push(uri); const next = lists(); if (next instanceof Error) throw next; return next; },
  });
  return { s, fetched };
}

test('list mode: the token and the list answer, and the list is fetched once, not once per check', async () => {
  const c = clock();
  const client = { verify: async () => { throw new Error('the live check must not be used'); }, token: async () => listedToken(5) };
  const { s, fetched } = listSidecar({ client, lists: () => statusList({ ended: [6] }), now: c.now });

  const first = await s.check('art-1', 'ORDER_UPDATES');
  assert.equal(first.decision, 'ALLOW');
  assert.equal(first.basis, 'status-list');
  assert.equal(first.staleUntil, new Date((NOW / 1000 + 60) * 1000).toISOString());
  for (let i = 0; i < 20; i++) await s.check('art-1', 'ORDER_UPDATES');
  assert.equal(fetched.length, 1);
  assert.equal(s.stats.listed, 21);
});

test('list mode: a withdrawal is seen when the held list is replaced, and says why', async () => {
  const c = clock();
  let list = statusList();
  const client = { token: async () => listedToken(5) };
  const { s } = listSidecar({ client, lists: () => list, now: c.now });
  assert.equal((await s.check('art-1', 'ORDER_UPDATES')).decision, 'ALLOW');

  c.advance(61);
  list = statusList({ ended: [5], exp: c.t / 1000 + 60 });
  assert.deepEqual(await s.check('art-1', 'ORDER_UPDATES'), { decision: 'DENY', reason: 'consent-ended', basis: 'status-list' });
});

test('list mode: an expired list that cannot be replaced is UNAVAILABLE, never a yes from stale data', async () => {
  const c = clock();
  let list = statusList();
  const client = { token: async () => listedToken(5) };
  const { s } = listSidecar({ client, lists: () => list, now: c.now });
  await s.check('art-1', 'ORDER_UPDATES');

  c.advance(61);
  list = new TypeError('down');
  const r = await s.check('art-1', 'ORDER_UPDATES');
  assert.equal(r.decision, 'UNAVAILABLE');
  assert.equal(r.reason, 'consent-check-unavailable');
});

test('list mode: a token that is not for this purpose, has no status, or names a foreign list is not an ALLOW', async () => {
  const c = clock();
  const run = async (tok, origin) => {
    const { s } = listSidecar({ client: { token: async () => tok }, lists: () => statusList(), now: c.now, origin });
    return s.check('art-1', 'ORDER_UPDATES');
  };
  assert.equal((await run(listedToken(5, { purpose_code: 'MARKETING' }))).reason, 'token-other-purpose');
  assert.equal((await run(token())).reason, 'token-without-status');
  assert.equal((await run(listedToken(5), 'https://other.example')).reason, 'status-list-foreign');
  assert.equal((await run(null)).reason, 'consent-not-in-force');
  assert.equal((await run(listedToken(99_999))).reason, 'status-index-outside-list');
});
