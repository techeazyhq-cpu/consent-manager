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

// The fiduciary-side consent sidecar (sdk/pep-sidecar, ADR-076) against the real local stack: a throwaway principal gives a
// consent, and the sidecar, with the Consent Manager sometimes unreachable (simulated at the sidecar's own network edge), is asked.
//   - a live check allows it; the real consent token verifies offline against the keys the platform publishes
//   - an outage with grace off is UNAVAILABLE; with grace on, the recent ALLOW is bridged by the token and says until when
//   - the principal withdraws; the next check is a DENY, and an outage after it is never bridged
//   - over HTTP, UNAVAILABLE is 503 with Retry-After
// Needs the keys file from setup-keys.mjs.
//
//   KEYS_FILE=/tmp/cm-keys.json node tests/security/e2e-pep-sidecar.mjs

import { createServer } from 'node:http';
import { randomBytes } from 'node:crypto';
import { GW, call, dpSignIn, loadKeys } from './lib/cm1.mjs';
import { ConsentClient, Sidecar, cachedJwks, verifyToken } from '../../sdk/pep-sidecar/src/sidecar.mjs';
import { handler } from '../../sdk/pep-sidecar/src/server.mjs';

const KEYS = loadKeys(process.env.KEYS_FILE ?? (() => { throw new Error('Set KEYS_FILE to the file setup-keys.mjs wrote'); })());
let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const dpApi = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};

const dp = `sidecar-${randomBytes(3).toString('hex')}@sidecar.example.test`;
const raised = (await call(KEYS.a, 'POST', '/api/cns-cmd/cns-reqs', { body: { extDpRefTyp: 'EMAIL', extDpRefVal: dp, prpsCode: 'ORDER_UPDATES', prpsVer: KEYS.prpsVerA } })).json.id;
const principal = await dpSignIn(dp);
await dpApi(principal, 'POST', '/api/cns-cmd/dp-links', { extDpRefTyp: 'EMAIL', extDpRefVal: dp });
const art = (await dpApi(principal, 'POST', `/api/cns-cmd/cns-reqs/${raised}/approve`)).json.id;
expect(Boolean(art), 'a principal has given a consent', art?.slice(0, 8));

// the Consent Manager is reachable unless the test says otherwise
let outage = false;
const flaky = async (...args) => {
  if (outage) throw new TypeError('fetch failed');
  return fetch(...args);
};
const client = new ConsentClient({ gateway: GW, apiKey: KEYS.a.apiKey, signingSecret: KEYS.a.signingSecret, fetchImpl: flaky });
const jwks = cachedJwks({ gateway: GW, fetchImpl: fetch });
const mk = (graceSeconds) => new Sidecar({ client, jwks, cacheSeconds: 0, graceSeconds });

const live = await mk(0).check(art, 'ORDER_UPDATES');
expect(live.decision === 'ALLOW' && live.basis === 'live', 'a live check allows it', JSON.stringify(live));

const tkn = await client.token(art);
const checked = verifyToken(tkn, await jwks(), { cnsArtId: art, purpose: 'ORDER_UPDATES' });
expect(checked.ok, 'the real consent token verifies offline against the published keys', checked.reason ?? `exp in ${Math.round(checked.claims.exp - Date.now() / 1000)} s`);
expect(!verifyToken(tkn, await jwks(), { cnsArtId: art, purpose: 'MARKETING' }).ok, 'and is refused for another purpose');

outage = true;
const off = mk(0);
expect((await off.check(art, 'ORDER_UPDATES')).decision === 'UNAVAILABLE', 'an outage with grace off is UNAVAILABLE');
outage = false;

const withGrace = mk(120);
await withGrace.check(art, 'ORDER_UPDATES');
outage = true;
const bridged = await withGrace.check(art, 'ORDER_UPDATES');
expect(bridged.decision === 'ALLOW' && bridged.basis === 'offline-token' && Date.parse(bridged.staleUntil) > Date.now(), 'with grace on the recent ALLOW is bridged by the token, with a stale-until time', JSON.stringify(bridged));
outage = false;

expect((await dpApi(principal, 'POST', `/api/cns-cmd/cns-arts/${art}/revoke`)).status === 200, 'the principal withdraws the consent');
const denied = await withGrace.check(art, 'ORDER_UPDATES');
expect(denied.decision === 'DENY' && denied.basis === 'live', 'the next check is a DENY', JSON.stringify(denied));
outage = true;
expect((await withGrace.check(art, 'ORDER_UPDATES')).decision === 'UNAVAILABLE', 'and an outage after it is never bridged');

const server = createServer(handler(withGrace));
await new Promise((r) => server.listen(0, '127.0.0.1', r));
try {
  const res = await fetch(`http://127.0.0.1:${server.address().port}/v1/check`, { method: 'POST', body: JSON.stringify({ cnsArtId: art, purpose: 'ORDER_UPDATES' }) });
  expect(res.status === 503 && Number(res.headers.get('retry-after')) > 0, 'over HTTP an outage is 503 with Retry-After', `${res.status} ${res.headers.get('retry-after')}`);
} finally {
  server.close();
}

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
