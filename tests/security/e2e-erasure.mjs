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

// A principal closing their account, end to end on the local stack (ADR-059). A fiduciary asks a new principal for
// three consents; the principal gives one, leaves one waiting, and a third is sent after they last signed in (never
// claimed). They register a device and make a request to the fiduciary. Then they close their account, and every
// step is checked: the notice in Mailpit, the consent withdrawn and the others declined (the fiduciary sees it), the
// request withdrawn with its words gone, the account deleted, and the saga DONE for staff. Exits non-zero on the first
// failure.
//
//   node tests/security/e2e-erasure.mjs

import { randomBytes } from 'node:crypto';
import { GW, MAILPIT, dfSignIn, dpSignIn, staffSignIn } from './lib/cm1.mjs';

const api = async (token, method, path, body) => {
  const res = await fetch(new URL(path, GW), { method, headers: { Authorization: `Bearer ${token}`,
    ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not JSON */ }
  return { status: res.status, json, text };
};
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) process.exit(1);
};
const until = async (fn, tries = 45) => {
  for (let i = 0; i < tries; i++) {
    const v = await fn();
    if (v) return v;
    await new Promise((ok) => setTimeout(ok, 1000));
  }
  return null;
};

const principal = `erase-${randomBytes(3).toString('hex')}@example.test`;
const dfA = await dfSignIn(process.env.DF_A_OWNER || 'batch-owner@example.test');
const prps = ((await api(dfA, 'GET', '/api/cns-cmd/prps-defs')).json ?? []).find((p) => p.currentVer);
const ask = async () => {
  const r = await api(dfA, 'POST', '/api/cns-cmd/cns-reqs', { extDpRefTyp: 'EMAIL', extDpRefVal: principal,
    prpsCode: prps.code, prpsVer: prps.currentVer });
  if (r.status !== 201) throw new Error(`raise: ${r.status} ${r.text}`);
  return r.json.id;
};

// ---- a principal with a consent given, one waiting, a device and an open request
const given = await ask();
const waiting = await ask();
const dp = await dpSignIn(principal);
await api(dp, 'POST', '/api/cns-cmd/dp-links', { extDpRefTyp: 'EMAIL', extDpRefVal: principal });
await until(async () => ((await api(dp, 'GET', '/api/cns-qry/cns-arts')).json ?? []).length === 2);
const approved = await api(dp, 'POST', `/api/cns-cmd/cns-reqs/${given}/approve`, {});
expect(approved.status === 200 || approved.status === 201, 'the principal gives one consent', approved.status);
const unclaimed = await ask(); // sent after they last signed in: still addressed to their email only
const device = await api(dp, 'POST', '/api/ntf/push-dvcs', { token: `e2e-${randomBytes(8).toString('hex')}`, platform: 'WEB' });
expect(device.status === 200 || device.status === 201, 'the principal registers a device', device.status);
await until(async () => ((await api(dp, 'GET', '/api/cns-qry/cns-arts')).json ?? [])
  .some((r) => r.cnsReqId === given && r.lcySts === 'ISSUED'));
const req = await api(dp, 'POST', '/api/dsr/me/requests', { kind: 'RELAY', cnsReqId: given, right: 'ACCESS',
  details: 'Please send me what you hold' });
expect(req.status === 201, 'the principal makes a request to the fiduciary', req.status);

// ---- closing the account
const wrong = await api(dp, 'POST', '/api/dsr/me/account/close', { confirmation: 'yes please' });
expect(wrong.status === 400, 'closing needs the typed confirmation', `${wrong.status} ${wrong.json?.title ?? ''}`);
const closing = await api(dp, 'POST', '/api/dsr/me/account/close', { confirmation: 'CLOSE MY ACCOUNT' });
expect(closing.status === 202 && closing.json.sts === 'RUNNING', 'the principal closes their account', closing.json?.step);
const twice = await api(dp, 'POST', '/api/dsr/me/account/close', { confirmation: 'CLOSE MY ACCOUNT' });
expect(twice.status === 409, 'a second close while the first runs is 409', twice.status);

const staff = await staffSignIn(process.env.STAFF || 'operator@cm.local');
const done = await until(async () => ((await api(staff, 'GET', '/api/dsr/erasures')).json ?? [])
  .find((e) => e.id === closing.json.id && e.sts === 'DONE'), 90);
expect(!!done, 'the erasure finishes, and staff see it DONE', done && `${done.consentsWithdrawn} withdrawn or declined`);
expect(done.consentsWithdrawn === 3, 'one consent withdrawn and two requests declined, the unclaimed one included',
  done.consentsWithdrawn);

// ---- every step's effect
const notice = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${principal}" subject:"being closed"`)}`)).json();
expect((notice.messages ?? []).length >= 1, 'the notice reached their inbox');
const status = async (id) => (await api(dfA, 'GET', `/api/cns-qry/cns-arts/${id}`)).json?.lcySts;
const statuses = await until(async () => {
  const s = [await status(given), await status(waiting), await status(unclaimed)];
  return s[0] === 'REVOKED' && s[1] === 'DENIED' && s[2] === 'DENIED' ? s : null;
});
expect(!!statuses, 'the fiduciary sees the consent withdrawn and both requests declined', statuses?.join(', '));
const relayed = (await api(dfA, 'GET', `/api/dsr/df/requests/${req.json.id}`)).json;
expect(relayed?.sts === 'WITHDRAWN' && relayed.details === null, "the request is withdrawn and the principal's words erased",
  relayed?.sts);
const after = await api(dp, 'GET', '/api/cns-qry/cns-arts');
expect(after.status === 200 || after.status === 401, 'the old token may run out its few minutes, but the account is gone');
const kc = await fetch(`${process.env.KC_BASE || 'http://localhost:8080'}/realms/master/protocol/openid-connect/token`, {
  method: 'POST', body: new URLSearchParams({ grant_type: 'password', client_id: 'admin-cli', username: 'admin',
    password: 'admin' }) }).then((r) => r.json());
const users = await fetch(`${process.env.KC_BASE || 'http://localhost:8080'}/admin/realms/cm-dp/users?email=${encodeURIComponent(principal)}&exact=true`,
  { headers: { Authorization: `Bearer ${kc.access_token}` } }).then((r) => r.json());
expect(Array.isArray(users) && users.length === 0, 'their Keycloak account is deleted');
console.log(`All checks passed. Erasure ${closing.json.id}, principal ${closing.json.dpId}.`);
