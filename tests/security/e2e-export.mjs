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

// A fiduciary taking its own data as one JSON file, end to end on the local stack (ADR-064). Uses the two long-lived test
// fiduciaries (it only reads, and leaves one audit entry each, "tenant.exported"):
//   - the Owner gets a bundle with its profile, team, API key list, purposes, consent records with receipts and audit entries
//   - no secret is in it: not an API key, a signing secret, a hash, or the client secret
//   - it holds only its own fiduciary's data, never the other's
//   - staff and other fiduciaries' people can't use the route
// Needs the keys file from setup-keys.mjs (for the two fiduciaries' ids and the data).
//
//   KEYS_FILE=/tmp/cm-keys.json node tests/security/e2e-export.mjs

import { GW, dfSignIn, loadKeys } from './lib/cm1.mjs';

const KEYS = loadKeys(process.env.KEYS_FILE ?? (() => { throw new Error('Set KEYS_FILE to the file setup-keys.mjs wrote'); })());
let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const get = async (token, path) => {
  const res = await fetch(`${GW}${path}`, { headers: { Authorization: `Bearer ${token}` } });
  const text = await res.text();
  return { status: res.status, headers: res.headers, text, json: text ? JSON.parse(text) : undefined };
};

const ownerA = await dfSignIn('batch-owner@example.test');
const a = await get(ownerA, '/api/df-reg/me/export');
expect(a.status === 200, 'the Owner downloads the export', `${a.status}, ${(a.text.length / 1024).toFixed(0)} KB`);
expect((a.headers.get('content-disposition') ?? '').includes('consent-manager-export-'), 'as a file', a.headers.get('content-disposition'));
const b = a.json;
expect(b['@type'] === 'cm:FiduciaryExport' && b.fiduciary.id === KEYS.dfA, 'it is the organisation\'s own', b.fiduciary?.name);
expect(Array.isArray(b.team) && b.team.some((m) => m.email === 'batch-owner@example.test' && m.role === 'OWNER'), 'with its team', `${b.team?.length} member(s)`);
expect(b.apiKeys.length > 0 && b.apiKeys.every((k) => k.keyPrefix && !('keyHash' in k) && !('apiKey' in k)), 'its API keys by prefix only');  // no detail: a key list is sensitive, even a count of it
expect(b.purposes.some((p) => p.code === 'ORDER_UPDATES' && p.versions.length > 0), 'its purposes with their versions', `${b.purposes.length} purpose(s)`);
expect(b.consents.count > 0 && b.consents.items.every((i) => i.record && Array.isArray(i.receipts)), 'its consent records with receipts', `${b.consents.count} record(s), truncated ${b.consents.truncated}`);
expect(b.audit.count > 0 && b.audit.items.every((e) => e.entryHash), 'its audit entries', `${b.audit.count} entries, truncated ${b.audit.truncated}`);
expect(Array.isArray(b.termsAcceptances), 'and its acceptances of the terms');

for (const secret of ['cms_', 'signingSecret', 'signing_secret', 'sealed', 'keyHash', 'cmDfApiClientSecret']) {
  expect(!a.text.includes(secret), `no "${secret}" anywhere in it`);
}
const otherDfId = KEYS.dfB;
expect(!a.text.includes(otherDfId), 'nothing of the other fiduciary is in it');
expect(b.consents.items.every((i) => !JSON.stringify(i).includes('security-b.example.test')), 'nor of its people');

const ownerB = await dfSignIn('contact@security-b.example.test');
const theirs = await get(ownerB, '/api/df-reg/me/export');
expect(theirs.status === 200 && theirs.json.fiduciary.id === KEYS.dfB && !theirs.text.includes(KEYS.dfA), 'the other fiduciary gets only its own', theirs.json?.fiduciary?.name);

const staff = await get(KEYS.staff, '/api/df-reg/me/export');
expect(staff.status === 403, 'platform staff cannot use the route', `${staff.status}`);
const anon = await fetch(`${GW}/api/df-reg/me/export`);
expect(anon.status === 401, 'nor can anyone without signing in', `${anon.status}`);

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
