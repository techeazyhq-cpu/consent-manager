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

// A fiduciary leaving, end to end on the local stack (ADR-064). Registers and activates a throwaway fiduciary (a departure
// can be cancelled only once, so each run needs its own), then:
//   - the Owner must type the organisation's name; a wrong name starts nothing
//   - leaving begins a 30-day wind-down, shown to the team, and the team is emailed
//   - during it the API key is refused with df-leaving and no new key can be issued, but the team can still read
//   - cancelling restores everything; a second departure cannot be cancelled
// The throwaway fiduciary is left leaving (closing comes with the next piece), named "Leaving Test …".
//
//   node tests/security/e2e-leaving.mjs

import { randomBytes } from 'node:crypto';
import { GW, KC, MAILPIT, call, dfSignIn } from './lib/cm1.mjs';

let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const bearer = (t) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' });
const api = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: bearer(token), body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};

const staff = (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local', password: 'admin' }) })).json()).access_token;

const tag = randomBytes(4).toString('hex');
const name = `Leaving Test ${tag}`;
const contact = `leaving-${tag}@leaving.example.test`;
const df = (await api(staff, 'POST', '/api/df-reg/dfs', { name, contactEmail: contact })).json;
await api(staff, 'POST', `/api/df-reg/dfs/${df.id}/activate`);
const owner = await dfSignIn(contact);

const issued = (await api(owner, 'POST', '/api/df-reg/me/api-keys', { name: 'Leaving test', signingRequired: false })).json;
const key = { apiKey: issued.apiKey, signingSecret: issued.signingSecret };
const LIST = '/api/cns-cmd/prps-defs';
expect((await call(key, 'GET', LIST)).status === 200, 'the new fiduciary\'s key works');

const wrong = await api(owner, 'POST', '/api/df-reg/me/leave', { organisationName: 'Some Other Ltd' });
expect(wrong.status === 400 && wrong.json.code === 'invalid-request', 'a wrong name starts nothing', `${wrong.status} ${wrong.json?.code}`);
expect((await api(owner, 'GET', '/api/df-reg/me/leave')).json.sts === 'ACTIVE', 'still active');

const left = await api(owner, 'POST', '/api/df-reg/me/leave', { organisationName: name.toLowerCase() });
const days = Math.round((Date.parse(left.json?.leavesAt) - Date.parse(left.json?.leavingAt)) / 86_400_000);
expect(left.status === 200 && left.json.sts === 'LEAVING' && days === 30 && left.json.canCancel, 'the Owner leaves; a 30-day wind-down begins', `${left.status} ${left.json?.sts}, ${days} days`);
expect((await api(owner, 'POST', '/api/df-reg/me/leave', { organisationName: name })).status === 409, 'leaving twice is refused');

const gone = await call(key, 'GET', LIST);
expect(gone.status === 403 && gone.json?.code === 'df-leaving', 'its API key is refused with df-leaving', `${gone.status} ${gone.json?.code}`);
const newKey = await api(owner, 'POST', '/api/df-reg/me/api-keys', { name: 'Too late', signingRequired: false });
expect(newKey.status === 403, 'no new key can be issued', `${newKey.status}`);
expect((await api(owner, 'GET', '/api/df-reg/me/api-keys')).status === 200, 'the team can still read');
const listed = (await api(staff, 'GET', '/api/df-reg/dfs')).json.find((d) => d.id === df.id);
expect(listed.sts === 'LEAVING' && !!listed.leavesAt, 'staff see it leaving');

let mail = 0;
for (let i = 0; i < 10 && !mail; i++) {
  await new Promise((r) => setTimeout(r, 1000));
  const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${contact} subject:"is leaving"`)}`)).json();
  mail = found.messages_count ?? found.total ?? 0;
}
expect(mail > 0, 'the team was emailed', `${mail} message(s)`);

const back = await api(owner, 'POST', '/api/df-reg/me/leave/cancel');
expect(back.status === 200 && back.json.sts === 'ACTIVE' && !back.json.leavesAt, 'the Owner cancels; it is active again', `${back.status} ${back.json?.sts}`);
expect((await call(key, 'GET', LIST)).status === 200, 'the same key works again');
expect((await api(owner, 'POST', '/api/df-reg/me/leave/cancel')).json?.code === 'df-not-leaving', 'nothing left to cancel');

const again = await api(owner, 'POST', '/api/df-reg/me/leave', { organisationName: name });
expect(again.status === 200 && again.json.canCancel === false, 'leaving again: this time it cannot be cancelled', `canCancel ${again.json?.canCancel}`);
const used = await api(owner, 'POST', '/api/df-reg/me/leave/cancel');
expect(used.status === 409 && used.json.code === 'leave-cancel-used', 'the second cancel is refused', `${used.status} ${used.json?.code}`);
const staffLeave = await api(staff, 'POST', '/api/df-reg/me/leave', { organisationName: name });
expect(staffLeave.status === 403, 'platform staff cannot end a fiduciary this way', `${staffLeave.status}`);

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
