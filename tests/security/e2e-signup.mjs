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

// An organisation applying for itself, end to end on the local stack (ADR-065), with no sign-in for the applicant:
//   - applying gives the same answer for any valid address, and mails a link; nothing is created until it is used
//   - the link works once, with its own token; a wrong token or id is refused the same way
//   - confirming creates a fiduciary waiting for staff, marked as self-service; staff can reject it and the applicant is emailed why
//   - an address that is already a fiduciary is told so only after proving the mailbox
//   - one address is held to three applications a day, and one client to ten requests an hour (the gateway)
//   - an invalid application is refused
// It clears the gateway's sign-up counters in Redis (docker exec) before and after, so it can be run again, and leaves
// throwaway "Signup Test …" fiduciaries behind (one rejected).
//
//   node tests/security/e2e-signup.mjs

import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { GW, KC, MAILPIT } from './lib/cm1.mjs';

let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const post = async (path, body, token) => {
  const res = await fetch(`${GW}${path}`, { method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(body) });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};
const clearIpLimit = () => {
  try {
    const keys = execFileSync('docker', ['exec', 'common-redis-1', 'redis-cli', '--scan', '--pattern', 'ratelimit:signup:*'], { stdio: 'pipe' }).toString().split('\n').filter(Boolean);
    for (const k of keys) execFileSync('docker', ['exec', 'common-redis-1', 'redis-cli', 'del', k], { stdio: 'pipe' });
  } catch {
    // the limit just stays as it was
  }
};
const mailTo = async (address, subject) => {
  for (let i = 0; i < 12; i++) {
    const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${address} subject:"${subject}"`)}&limit=1`)).json();
    const m = found.messages?.[0];
    if (m) return (await (await fetch(`${MAILPIT}/api/v1/message/${m.ID}`)).json()).Text;
    await sleep(1000);
  }
  return null;
};
const linkOf = (text) => {
  const m = /id=([0-9a-f-]{36})&token=([A-Za-z0-9_-]+)/.exec(text ?? '');
  return m ? { id: m[1], token: m[2] } : null;
};
const staff = (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local', password: 'admin' }) })).json()).access_token;
const dfsOf = async () => (await (await fetch(`${GW}/api/df-reg/dfs`, { headers: { Authorization: `Bearer ${staff}` } })).json());

clearIpLimit();
const tag = randomBytes(4).toString('hex');
const address = `signup-${tag}@signup.example.test`;
const application = { name: `Signup Test ${tag}`, contactEmail: address, legalName: `Signup Test ${tag} Private Limited`, industry: 'Retail', website: 'https://signup.example.test' };

// apply: no sign-in, no fiduciary yet
const applied = await post('/api/df-reg/signup', application);
expect(applied.status === 202, 'an organisation applies with no sign-in', `${applied.status}`);
expect(!(await dfsOf()).some((d) => d.name === application.name), 'and nothing is created until the address is confirmed');
const text = await mailTo(address, 'Confirm your Consent Manager application');
const link = linkOf(text);
expect(!!link, 'the contact address is mailed a link', link ? 'id and token found' : 'no link');
expect(text.includes('#/signup/verify?'), 'that opens the sign-up page');

// the link
const wrong = await post('/api/df-reg/signup/verify', { id: link.id, token: 'not-the-token' });
expect(wrong.status === 400 && wrong.json.code === 'signup-invalid-token', 'a wrong token is refused', `${wrong.status} ${wrong.json?.code}`);
const unknownId = await post('/api/df-reg/signup/verify', { id: '00000000-0000-0000-0000-000000000000', token: link.token });
expect(unknownId.status === 400 && unknownId.json.code === 'signup-invalid-token' && unknownId.json.detail === wrong.json.detail, 'a wrong id is refused in the same words');
const ok = await post('/api/df-reg/signup/verify', link);
expect(ok.status === 200 && ok.json.status === 'RECEIVED', 'the link confirms the address', `${ok.status}`);
const created = (await dfsOf()).find((d) => d.name === application.name);
expect(created?.sts === 'PENDING' && !!created.signedUpAt && created.contactEmail === address, 'a fiduciary now waits for staff, marked as self-service', `${created?.sts}`);
expect(created.legalName === application.legalName && created.industry === 'Retail', 'with the profile it gave');
const again = await post('/api/df-reg/signup/verify', link);
expect(again.status === 400 && again.json.code === 'signup-invalid-token', 'the link works once', `${again.status}`);
expect(!!(await mailTo(address, 'We received your application')), 'the applicant is told it is with the team');

// the same address again: the same answer, and only the mailbox's owner is told
clearIpLimit();
const second = await post('/api/df-reg/signup', { ...application, name: `${application.name} again` });
expect(second.status === 202 && second.json.message === applied.json.message, 'applying for a known address gives the same answer', `${second.status}`);
let secondText = null;
for (let i = 0; i < 12 && !secondText; i++) {
  const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${address} subject:"Confirm your"`)}&limit=5`)).json();
  const latest = found.messages?.[0];
  const body = latest ? (await (await fetch(`${MAILPIT}/api/v1/message/${latest.ID}`)).json()).Text : null;
  if (linkOf(body) && linkOf(body).id !== link.id) secondText = body;
  else await sleep(1000);
}
const secondLink = linkOf(secondText);
const known = await post('/api/df-reg/signup/verify', secondLink);
expect(known.status === 409 && known.json.code === 'df-already-registered', 'the mailbox\'s owner is told it is already registered', `${known.status} ${known.json?.code}`);

// one address, three a day
clearIpLimit();
const third = await post('/api/df-reg/signup', { ...application, name: `${application.name} third` });
const fourth = await post('/api/df-reg/signup', { ...application, name: `${application.name} fourth` });
expect(third.status === 202 && fourth.status === 429 && fourth.json.code === 'signup-rate-limited', 'one address is held to three applications a day', `${third.status}, then ${fourth.status} ${fourth.json?.code}`);

// staff reject
const rejectReason = 'E2E test: registration number not found';
const rejected = await post(`/api/df-reg/dfs/${created.id}/reject`, { reason: rejectReason }, staff);
expect(rejected.status === 200 && rejected.json.sts === 'REJECTED' && rejected.json.rejectionReason === rejectReason, 'staff reject the application', `${rejected.status} ${rejected.json?.sts}`);
const why = await mailTo(address, 'About your application');
expect(why?.includes(rejectReason), 'and the applicant is emailed why');
expect((await post(`/api/df-reg/dfs/${created.id}/reject`, { reason: 'again' }, staff)).status === 409, 'a rejected application cannot be rejected again');
expect((await post(`/api/df-reg/dfs/${created.id}/activate`, {}, staff)).status === 409, 'nor activated');
expect((await post(`/api/df-reg/dfs/${created.id}/reject`, {})).status === 401, 'rejecting needs staff');

// invalid
clearIpLimit();
const bad = await post('/api/df-reg/signup', { name: 'Nobody', contactEmail: 'not-an-address' });
expect(bad.status === 400 && bad.json.code === 'invalid-df-profile', 'an invalid application is refused', `${bad.status} ${bad.json?.code}`);
const empty = await post('/api/df-reg/signup', { name: ' ', contactEmail: 'x@signup.example.test' });
expect(empty.status === 400, 'so is one with no name', `${empty.status}`);

// the gateway's limit per client
clearIpLimit();
const statuses = [];
for (let i = 0; i < 13; i++) statuses.push((await post('/api/df-reg/signup/verify', { id: 'x', token: 'y' })).status);
expect(statuses.slice(0, 10).every((s) => s === 400) && statuses.slice(10).every((s) => s === 429), 'one client is held to ten requests an hour by the gateway', statuses.join(' '));
clearIpLimit();

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
