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

// A fiduciary's register of processors, end to end on the local stack (ADR-075): a throwaway fiduciary registers one, a
// duplicate and bad input are refused, another fiduciary cannot see or retire it, a purpose names it, `uses` finds the purpose,
// and once retired a new purpose or a publication that names it is refused until it is removed or the processor reinstated.
//
//   node tests/security/e2e-processors.mjs

import { randomBytes } from 'node:crypto';
import { GW, KC, dfSignIn } from './lib/cm1.mjs';

let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const api = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};

const admin = (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local', password: 'admin' }) })).json()).access_token;
const tag = randomBytes(4).toString('hex');
const contact = `processors-${tag}@processors.example.test`;
const df = (await api(admin, 'POST', '/api/df-reg/dfs', { name: `Processors Test ${tag}`, contactEmail: contact })).json;
await api(admin, 'POST', `/api/df-reg/dfs/${df.id}/activate`);
const owner = await dfSignIn(contact);
const other = await dfSignIn('contact@security-b.example.test');

const content = (...recipients) => ({
  name: 'Order updates', description: '', recipients, transfers: [], retentionDays: 365, requestedValidityDays: 180, maxValidityDays: 365, flags: [],
  dCats: [{ code: 'EMAIL', dpv: null, sensitivity: 'ORDINARY', necessary: false, consequence: null }],
  notices: [{ lang: 'en', title: 'Order updates', text: 'We email you about your orders.' }],
});

const made = await api(owner, 'POST', '/api/cns-cmd/processors', { name: '  Courier   Ltd ', role: 'Delivers orders', country: 'in', contactEmail: 'ops@courier.example' });
expect(made.status === 201 && made.json.name === 'Courier Ltd' && made.json.country === 'IN' && made.json.status === 'ACTIVE', 'the fiduciary registers a processor, tidied', `${made.status} ${made.json.name} ${made.json.country}`);
const id = made.json.id;

const dup = await api(owner, 'POST', '/api/cns-cmd/processors', { name: 'COURIER ltd' });
expect(dup.status === 409 && dup.json.code === 'processor-exists', 'the same name again is refused', `${dup.status} ${dup.json.code}`);
const bad = await api(owner, 'POST', '/api/cns-cmd/processors', { name: 'X', country: 'India' });
expect(bad.status === 400 && bad.json.code === 'invalid-processor', 'a bad country is refused', `${bad.status} ${bad.json.code}`);

const seenByOther = await api(other, 'GET', '/api/cns-cmd/processors');
expect(seenByOther.status === 200 && !seenByOther.json.some((p) => p.id === id), 'another fiduciary does not see it');
const retireByOther = await api(other, 'POST', `/api/cns-cmd/processors/${id}/retire`);
expect(retireByOther.status === 404 && retireByOther.json.code === 'processor-not-found', 'and cannot retire it', `${retireByOther.status}`);

const code = `ORDERS_${tag.toUpperCase()}`;
const purpose = await api(owner, 'POST', '/api/cns-cmd/prps-defs', { code, content: content('Courier Ltd', 'Somebody Unregistered Inc') });
expect(purpose.status === 201, 'a purpose names the processor, and a name that is not in the register is accepted too', `${purpose.status}`);
expect((await api(owner, 'POST', `/api/cns-cmd/prps-defs/${code}/publish`)).status === 200, 'and is published');

const version = (await api(owner, 'GET', `/api/cns-cmd/prps-defs/${code}`)).json.versions.find((v) => v.sts === 'PUBLISHED');
expect(version?.content.parties?.length === 1 && version.content.parties[0].name === 'Courier Ltd' && version.content.parties[0].role === 'Delivers orders' && version.content.parties[0].country === 'IN', 'the published version carries what the register said about the party it names, and only the registered one', JSON.stringify(version?.content.parties));

const uses = await api(owner, 'GET', `/api/cns-cmd/processors/${id}/uses`);
expect(uses.status === 200 && uses.json.length === 1 && uses.json[0].purpose === code, 'uses finds the purpose', JSON.stringify(uses.json));

const edited = await api(owner, 'PUT', `/api/cns-cmd/processors/${id}`, { role: 'Something else', country: 'US', contactEmail: '' });
const stillFrozen = (await api(owner, 'GET', `/api/cns-cmd/prps-defs/${code}`)).json.versions.find((v) => v.sts === 'PUBLISHED');
expect(edited.status === 200 && stillFrozen.content.parties[0].role === 'Delivers orders' && stillFrozen.content.parties[0].country === 'IN', 'editing the register afterwards does not change the published version');
expect((await api(owner, 'POST', `/api/cns-cmd/processors/${id}/retire`)).json.status === 'RETIRED', 'the processor is retired');
const created = await api(owner, 'POST', '/api/cns-cmd/prps-defs', { code: `${code}_2`, content: content('courier ltd') });
expect(created.status === 409 && created.json.code === 'processor-retired', 'a new purpose cannot name it', `${created.status} ${created.json.code}`);
const draft = await api(owner, 'PUT', `/api/cns-cmd/prps-defs/${code}/draft`, content('Courier Ltd'));
expect(draft.status === 409 && draft.json.code === 'processor-retired', 'nor can a new draft', `${draft.status}`);
const unchanged = await api(owner, 'GET', `/api/cns-cmd/prps-defs/${code}`);
expect(unchanged.json.versions?.length === 1 || unchanged.json.current != null, 'the published version that already named it is unchanged');

expect((await api(owner, 'POST', `/api/cns-cmd/processors/${id}/reinstate`)).json.status === 'ACTIVE', 'reinstating allows it again');
const again = await api(owner, 'PUT', `/api/cns-cmd/prps-defs/${code}/draft`, content('Courier Ltd'));
expect(again.status === 200, 'and a draft may name it', `${again.status}`);

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
