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

// A fiduciary accepting the consent manager's terms and data processing agreement, end to end on the local stack
// (ADR-063): the Owner of Batch Test Co accepts the current version, an old version and an unknown document are refused,
// accepting twice keeps the first acceptance, platform staff read the evidence, and no one else can read or accept.
// Acceptances are evidence and are never removed, so a re-run finds the earlier ones; it only adds nothing.
//
//   node tests/security/e2e-terms.mjs

import { GW, KC, dfSignIn } from './lib/cm1.mjs';

const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) process.exit(1);
};
const bearer = (t) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' });
const api = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: bearer(token), body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};

const staff = (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local', password: 'admin' }) })).json()).access_token;
const dfs = (await api(staff, 'GET', '/api/df-reg/dfs')).json;
const dfA = dfs.find((d) => d.name === 'Batch Test Co');
const ownerA = await dfSignIn('batch-owner@example.test');
const ownerB = await dfSignIn('contact@security-b.example.test');

const before = await api(ownerA, 'GET', '/api/df-reg/me/terms');
expect(before.status === 200 && before.json.length === 2, 'the Owner sees both documents', before.json.map((s) => `${s.document} ${s.currentVersion}`).join(', '));
const terms = before.json.find((s) => s.document === 'terms');
const dpa = before.json.find((s) => s.document === 'dpa');

const old = await api(ownerA, 'POST', '/api/df-reg/me/terms', { document: 'terms', version: '1999-01' });
expect(old.status === 409 && old.json.code === 'terms-version-outdated', 'an old version is refused', `${old.status} ${old.json.code}`);
expect(old.json.detail.includes(terms.currentVersion), 'and the refusal names the current version');

const unknown = await api(ownerA, 'POST', '/api/df-reg/me/terms', { document: 'privacy', version: '1' });
expect(unknown.status === 400 && unknown.json.code === 'terms-document-unknown', 'an unknown document is refused', `${unknown.status} ${unknown.json.code}`);

const first = await api(ownerA, 'POST', '/api/df-reg/me/terms', { document: 'terms', version: terms.currentVersion });
expect(first.status === 201 && first.json.document === 'terms' && first.json.version === terms.currentVersion, 'the Owner accepts the terms', `${first.status}`);
expect(!!first.json.acceptedBy && !!first.json.acceptedAt, 'with who and when');
const again = await api(ownerA, 'POST', '/api/df-reg/me/terms', { document: 'TERMS', version: terms.currentVersion });
expect(again.status === 201 && again.json.id === first.json.id && again.json.acceptedAt === first.json.acceptedAt, 'accepting again keeps the first acceptance');
const dpaDone = await api(ownerA, 'POST', '/api/df-reg/me/terms', { document: 'dpa', version: dpa.currentVersion });
expect(dpaDone.status === 201, 'and the DPA');

const after = await api(ownerA, 'GET', '/api/df-reg/me/terms');
expect(after.json.every((s) => s.accepted && s.acceptance), 'the status shows both accepted');

const staffAccept = await api(staff, 'POST', '/api/df-reg/me/terms', { document: 'terms', version: terms.currentVersion });
expect(staffAccept.status === 403, 'platform staff cannot accept for a fiduciary', `${staffAccept.status}`);
const staffStatus = await api(staff, 'GET', '/api/df-reg/me/terms');
expect(staffStatus.status === 403, 'nor read a fiduciary\'s own status view', `${staffStatus.status}`);

const evidence = await api(staff, 'GET', `/api/df-reg/dfs/${dfA.id}/terms`);
expect(evidence.status === 200 && evidence.json.some((a) => a.id === first.json.id) && evidence.json.some((a) => a.id === dpaDone.json.id),
  'platform staff read the evidence', `${evidence.json.length} acceptance(s)`);
const spy = await api(ownerB, 'GET', `/api/df-reg/dfs/${dfA.id}/terms`);
expect(spy.status === 403, 'another fiduciary\'s Owner cannot read it', `${spy.status}`);

const other = await api(ownerB, 'GET', '/api/df-reg/me/terms');
const otherEvidence = await api(staff, 'GET', `/api/df-reg/dfs/${(dfs.find((d) => d.name === 'Security Test B Ltd')).id}/terms`);
expect(other.status === 200 && otherEvidence.json.every((a) => a.id !== first.json.id), 'another fiduciary\'s acceptances are its own');
console.log('\nAll checks passed.');
