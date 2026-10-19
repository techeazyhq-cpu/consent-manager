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

// Local stack only. Gives the security and performance kits two fiduciaries' API keys, so tests can try one
// fiduciary's key against the other's data:
//   A  Batch Test Co (batch-owner@example.test): a signing key, and a key that doesn't require signing
//   B  Security Test B Ltd, registered and activated by the seeded staff admin on PREMIUM (so load tests aren't held to
//      FREE's 5 requests a second); its contact (contact@security-b.example.test) is its owner. A signing key
// and, for each, a consent a test data principal (perf-dp@example.test) has given, so the verify path can be tested.
// Signs people in with emailed codes read from Mailpit, writes the keys and tokens to KEYS_FILE (outside the
// repository), and prints only ids.
//
//   KEYS_FILE=/tmp/cm-keys.json node tests/security/setup-keys.mjs

import { writeFileSync } from 'node:fs';
import { GW, KC, call, dfSignIn, dpSignIn } from './lib/cm1.mjs';

const KEYS_FILE = process.env.KEYS_FILE;
if (!KEYS_FILE) throw new Error('Set KEYS_FILE to a path outside the repository');

const json = async (res) => {
  const text = await res.text();
  if (!res.ok) throw new Error(`${res.status} ${res.url}: ${text.slice(0, 300)}`);
  return text ? JSON.parse(text) : undefined;
};
const bearer = (t, extra = {}) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json', ...extra });

// the seeded local staff admin (infra/keycloak/realm-export/cm-staff.json)
const staff = (await json(await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local',
    password: 'admin' }) }))).access_token;
const dfs = await json(await fetch(`${GW}/api/df-reg/dfs`, { headers: bearer(staff) }));
const dfA = dfs.find((d) => d.name === 'Batch Test Co');
if (!dfA) throw new Error('Expected the seeded Batch Test Co fiduciary');
let dfB = dfs.find((d) => d.name === 'Security Test B Ltd');
if (!dfB) {
  dfB = await json(await fetch(`${GW}/api/df-reg/dfs`, { method: 'POST', headers: bearer(staff),
    body: JSON.stringify({ name: 'Security Test B Ltd', contactEmail: 'contact@security-b.example.test', subPlnName: 'PREMIUM' }) }));
  await json(await fetch(`${GW}/api/df-reg/dfs/${dfB.id}/activate`, { method: 'POST', headers: bearer(staff) }));
}

// registering makes the contact the owner
const ownerB = 'contact@security-b.example.test';

const issue = async (token, name, signingRequired) => {
  const r = await json(await fetch(`${GW}/api/df-reg/me/api-keys`, { method: 'POST', headers: bearer(token),
    body: JSON.stringify({ name, signingRequired }) }));
  return { id: r.key.id, apiKey: r.apiKey, signingSecret: r.signingSecret };
};

const tokenA = await dfSignIn('batch-owner@example.test');
const tokenB = await dfSignIn(ownerB);
const keys = {
  dfA: dfA.id, dfB: dfB.id,
  a: await issue(tokenA, 'Security test (signed)', true),
  aUnsigned: await issue(tokenA, 'Security test (unsigned reads and writes)', false),
  aRevoked: await issue(tokenA, 'Security test (revoked)', true),
  b: await issue(tokenB, 'Security test (signed)', true),
  tokenA, tokenB, staff,
};
await json(await fetch(`${GW}/api/df-reg/me/api-keys/${keys.aRevoked.id}`, { method: 'DELETE', headers: bearer(tokenA) }));

// one given consent each: raised with the key, approved by the data principal in the portal's way
const dp = 'perf-dp@example.test';
const raised = {};
for (const who of ['a', 'b']) {
  const r = await call(keys[who], 'POST', '/api/cns-cmd/cns-reqs', { body: { extDpRefTyp: 'EMAIL', extDpRefVal: dp,
    prpsCode: 'ORDER_UPDATES', dCatCodes: ['EMAIL'], ntcTitle: 'Order updates', ntcText: 'Set up by the test kit.' } });
  if (r.status !== 201) throw new Error(`raise for ${who}: ${r.status} ${r.text.slice(0, 200)}`);
  raised[who] = r.json.id;
}
const tokenDp = await dpSignIn(dp);
await json(await fetch(`${GW}/api/cns-cmd/dp-links`, { method: 'POST', headers: bearer(tokenDp),
  body: JSON.stringify({ extDpRefTyp: 'EMAIL', extDpRefVal: dp }) }));
for (const who of ['a', 'b']) {
  const art = await json(await fetch(`${GW}/api/cns-cmd/cns-reqs/${raised[who]}/approve`, { method: 'POST',
    headers: bearer(tokenDp) }));
  keys[`granted${who.toUpperCase()}`] = { cnsReqId: raised[who], cnsArtId: art.id, purpose: 'ORDER_UPDATES' };
}
writeFileSync(KEYS_FILE, JSON.stringify(keys, null, 2), { mode: 0o600 });
console.log(`Issued keys ${keys.a.id}, ${keys.aUnsigned.id}, ${keys.aRevoked.id} (revoked) for A and ${keys.b.id} for B;`
  + ` consents ${keys.grantedA.cnsArtId} (A) and ${keys.grantedB.cnsArtId} (B)`);
