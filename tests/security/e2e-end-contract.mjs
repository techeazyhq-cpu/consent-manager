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

// Platform staff ending a fiduciary's contract with a second person's approval, end to end on the local stack (ADR-064
// D7), with the two seeded staff accounts (the operator proposes, the administrator decides). Uses a throwaway fiduciary:
//   - one person cannot do it alone: nothing happens on a proposal, and the proposer can't approve their own
//   - an operator can propose but not decide
//   - a rejection or a withdrawal changes nothing; only one proposal waits at a time
//   - an approval starts the 30-day wind-down, the key is refused with df-leaving, and the team is emailed
//   - calling it off takes two people too, and puts the fiduciary back
//   - a fiduciary's own people can't use any of it
// The throwaway "End Contract Test …" fiduciary is left active.
//
//   node tests/security/e2e-end-contract.mjs

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
const staffToken = async (username, password) => (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username, password }) })).json()).access_token;

const admin = await staffToken('admin@cm.local', 'admin');
const operator = await staffToken('operator@cm.local', 'operator');

const tag = randomBytes(4).toString('hex');
const name = `End Contract Test ${tag}`;
const contact = `endcontract-${tag}@endcontract.example.test`;
const df = (await api(admin, 'POST', '/api/df-reg/dfs', { name, contactEmail: contact })).json;
await api(admin, 'POST', `/api/df-reg/dfs/${df.id}/activate`);
const owner = await dfSignIn(contact);
const issued = (await api(owner, 'POST', '/api/df-reg/me/api-keys', { name: 'End contract test', signingRequired: false })).json;
const key = { apiKey: issued.apiKey, signingSecret: issued.signingSecret };
const LIST = '/api/cns-cmd/prps-defs';
const stsOf = async () => (await api(admin, 'GET', '/api/df-reg/dfs')).json.find((d) => d.id === df.id).sts;

// one person cannot do it alone
const noReason = await api(operator, 'POST', `/api/df-reg/dfs/${df.id}/end-contract`, {});
expect(noReason.status === 400, 'a reason is required', `${noReason.status}`);
const p1 = await api(admin, 'POST', `/api/df-reg/dfs/${df.id}/end-contract`, { reason: 'E2E test: proposed by the administrator' });
expect(p1.status === 201 && p1.json.status === 'PENDING', 'a proposal waits for a second person', `${p1.status} ${p1.json?.status}`);
expect(await stsOf() === 'ACTIVE', 'nothing has happened yet');
const self = await api(admin, 'POST', `/api/df-reg/leave-proposals/${p1.json.id}/approve`);
expect(self.status === 403 && self.json.code === 'leave-proposal-same-person', 'the proposer cannot approve their own', `${self.status} ${self.json?.code}`);
const selfReject = await api(admin, 'POST', `/api/df-reg/leave-proposals/${p1.json.id}/reject`, {});
expect(selfReject.status === 403 && selfReject.json.code === 'leave-proposal-same-person', 'nor reject it', `${selfReject.status}`);
const opApprove = await api(operator, 'POST', `/api/df-reg/leave-proposals/${p1.json.id}/approve`);
expect(opApprove.status === 403, 'an operator cannot decide (administrators only)', `${opApprove.status}`);
const second = await api(operator, 'POST', `/api/df-reg/dfs/${df.id}/end-contract`, { reason: 'another' });
expect(second.status === 409 && second.json.code === 'leave-proposal-pending', 'only one proposal waits at a time', `${second.status} ${second.json?.code}`);
const notProposer = await api(operator, 'POST', `/api/df-reg/leave-proposals/${p1.json.id}/withdraw`);
expect(notProposer.status === 403, 'only the proposer withdraws', `${notProposer.status}`);
const withdrawn = await api(admin, 'POST', `/api/df-reg/leave-proposals/${p1.json.id}/withdraw`);
expect(withdrawn.status === 200 && withdrawn.json.status === 'WITHDRAWN', 'the proposer withdraws it', `${withdrawn.json?.status}`);
const decidedAgain = await api(admin, 'POST', `/api/df-reg/leave-proposals/${p1.json.id}/withdraw`);
expect(decidedAgain.status === 409 && decidedAgain.json.code === 'leave-proposal-decided', 'a decided proposal is final', `${decidedAgain.status} ${decidedAgain.json?.code}`);

// a rejection changes nothing
const p2 = await api(operator, 'POST', `/api/df-reg/dfs/${df.id}/end-contract`, { reason: 'E2E test: to be rejected' });
const rejected = await api(admin, 'POST', `/api/df-reg/leave-proposals/${p2.json.id}/reject`, { note: 'Not enough evidence' });
expect(rejected.status === 200 && rejected.json.status === 'REJECTED' && rejected.json.decisionNote === 'Not enough evidence', 'a second person rejects it', `${rejected.json?.status}`);
expect(await stsOf() === 'ACTIVE' && (await call(key, 'GET', LIST)).status === 200, 'and nothing changes');

// an approval starts the wind-down
const p3 = await api(operator, 'POST', `/api/df-reg/dfs/${df.id}/end-contract`, { reason: 'E2E test: repeated abuse' });
const listed = (await api(admin, 'GET', `/api/df-reg/leave-proposals?status=PENDING&dfId=${df.id}`)).json;
expect(listed.length === 1 && listed[0].id === p3.json.id, 'the waiting proposal is listed', `${listed.length}`);
const approved = await api(admin, 'POST', `/api/df-reg/leave-proposals/${p3.json.id}/approve`);
expect(approved.status === 200 && approved.json.status === 'APPROVED' && approved.json.decidedBy !== approved.json.proposedBy, 'a different administrator approves', `${approved.status} ${approved.json?.status}`);
expect(await stsOf() === 'LEAVING', 'the departure starts');
const status = await api(owner, 'GET', '/api/df-reg/me/leave');
const days = Math.round((Date.parse(status.json?.leavesAt) - Date.parse(status.json?.leavingAt)) / 86_400_000);
expect(status.json?.sts === 'LEAVING' && days === 30, 'a 30-day wind-down, visible to its team', `${days} days`);
const gone = await call(key, 'GET', LIST);
expect(gone.status === 403 && gone.json?.code === 'df-leaving', 'its API key is refused with df-leaving', `${gone.status} ${gone.json?.code}`);
let mail = 0;
for (let i = 0; i < 10 && !mail; i++) {
  await new Promise((r) => setTimeout(r, 1000));
  const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${contact} subject:"ending its agreement"`)}`)).json();
  mail = found.messages_count ?? found.total ?? 0;
}
expect(mail > 0, 'the team is emailed that Consent Manager ended the agreement', `${mail} message(s)`);

// calling it off takes two people too
const c1 = await api(operator, 'POST', `/api/df-reg/dfs/${df.id}/cancel-end-contract`, { reason: 'E2E test: resolved' });
expect(c1.status === 201 && c1.json.kind === 'CANCEL', 'staff propose calling it off', `${c1.status}`);
expect(await stsOf() === 'LEAVING', 'still leaving until a second person agrees');
const c2 = await api(admin, 'POST', `/api/df-reg/leave-proposals/${c1.json.id}/approve`);
expect(c2.status === 200 && await stsOf() === 'ACTIVE', 'a second person approves; it is active again', `${c2.status}`);
expect((await call(key, 'GET', LIST)).status === 200, 'the same key works again');
const badCancel = await api(operator, 'POST', `/api/df-reg/dfs/${df.id}/cancel-end-contract`, { reason: 'nothing to cancel' });
expect(badCancel.status === 409, 'nothing to cancel when it is not leaving', `${badCancel.status}`);

// the fiduciary's own people can't use any of it
const ownersTry = await api(owner, 'POST', `/api/df-reg/dfs/${df.id}/end-contract`, { reason: 'I want out, my way' });
expect(ownersTry.status === 403, 'a fiduciary\'s Owner cannot use the staff route', `${ownersTry.status}`);
expect((await api(owner, 'GET', '/api/df-reg/leave-proposals')).status === 403, 'nor read the proposals');

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
