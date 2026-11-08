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

// A fiduciary closing when its wind-down ends, end to end on the local stack (ADR-064). Registers a throwaway fiduciary
// with a purpose, one consent a principal gave and one request still waiting, has its Owner leave, then makes the wind-down
// end now (it sets leaves_at in the past in the local database, because waiting 30 days is not a test) and waits for the
// closing pass (about a minute). Checks that:
//   - the fiduciary is CLOSED and its profile is erased, but its legal identity is kept
//   - its consent is ended and its waiting request declined, and the records are still there
//   - its API key is refused and its team's account is gone
// Needs the local database container (docker exec). It leaves a CLOSED "Closing Test …" fiduciary behind; closed is final.
//
//   node tests/security/e2e-closing.mjs

import { execFileSync } from 'node:child_process';
import { randomBytes } from 'node:crypto';
import { GW, KC, call, dfSignIn, dpSignIn } from './lib/cm1.mjs';

let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const bearer = (t) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' });
const api = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: bearer(token), body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};
const until = async (what, fn, { tries = 40, every = 3000 } = {}) => {
  for (let i = 0; i < tries; i++) {
    const v = await fn();
    if (v) return v;
    await sleep(every);
  }
  console.log(`(gave up waiting for ${what})`);
  return null;
};

const staff = (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local', password: 'admin' }) })).json()).access_token;

const tag = randomBytes(4).toString('hex');
const name = `Closing Test ${tag}`;
const contact = `closing-${tag}@closing.example.test`;
const dp = `closing-dp-${tag}@closing.example.test`;
const dpWaiting = `closing-wait-${tag}@closing.example.test`;

// a fiduciary with a published purpose, a consent a principal gave and a request still waiting
const df = (await api(staff, 'POST', '/api/df-reg/dfs', { name, contactEmail: contact })).json;
await api(staff, 'POST', `/api/df-reg/dfs/${df.id}/activate`);
const owner = await dfSignIn(contact);
const issued = (await api(owner, 'POST', '/api/df-reg/me/api-keys', { name: 'Closing test', signingRequired: false })).json;
const key = { apiKey: issued.apiKey, signingSecret: issued.signingSecret };
await call(key, 'POST', '/api/cns-cmd/prps-defs', { body: { code: 'ORDER_UPDATES', content: { name: 'Order updates',
  dCats: [{ code: 'EMAIL', sensitivity: 'ORDINARY', necessary: false }], recipients: [], transfers: [], retentionDays: 365,
  requestedValidityDays: 365, maxValidityDays: 365, flags: [], notices: [{ lang: 'en', title: 'Order updates', text: 'Set up by the test kit.' }] } } });
await call(key, 'POST', '/api/cns-cmd/prps-defs/ORDER_UPDATES/publish');
const prpsVer = (await call(key, 'GET', '/api/cns-cmd/prps-defs/ORDER_UPDATES')).json.currentVer;
const raise = (who) => call(key, 'POST', '/api/cns-cmd/cns-reqs', { body: { extDpRefTyp: 'EMAIL', extDpRefVal: who, prpsCode: 'ORDER_UPDATES', prpsVer } });
const given = (await raise(dp)).json;
const waiting = (await raise(dpWaiting)).json;
const tokenDp = await dpSignIn(dp);
await api(tokenDp, 'POST', '/api/cns-cmd/dp-links', { extDpRefTyp: 'EMAIL', extDpRefVal: dp });
const art = (await api(tokenDp, 'POST', `/api/cns-cmd/cns-reqs/${given.id}/approve`)).json;
expect(!!art?.id, 'a principal has given a consent, and another request is waiting', `art ${art?.id?.slice(0, 8)}, waiting ${waiting.id?.slice(0, 8)}`);

const reqs = async () => {
  const r = await api(staff, 'GET', `/api/cns-qry/admin/dfs/${df.id}/cns-reqs?limit=100`);
  const list = Array.isArray(r.json) ? r.json : (r.json?.items ?? r.json?.content ?? []);
  return Object.fromEntries(list.map((x) => [x.cnsReqId, x.lcySts]));
};
await until('the read model', async () => (await reqs())[given.id] === 'ISSUED');
const before = await reqs();
expect(before[given.id] === 'ISSUED' && before[waiting.id] === 'PENDING', 'before: one ISSUED, one PENDING', JSON.stringify(Object.values(before)));

// leave, then bring the end of the wind-down to now
const left = await api(owner, 'POST', '/api/df-reg/me/leave', { organisationName: name });
expect(left.status === 200 && left.json.sts === 'LEAVING', 'the Owner leaves', `${left.status} ${left.json?.sts}`);
execFileSync('docker', ['exec', 'common-postgres-1', 'psql', '-U', 'cm', '-d', 'consent_manager', '-c',
  `update df_reg.df set leaves_at = now() - interval '1 minute' where id = '${df.id}'`], { stdio: 'pipe' });
console.log('(the wind-down now ends; waiting for the closing pass, up to about two minutes)');

const closed = await until('the fiduciary to close', async () => {
  const d = (await api(staff, 'GET', '/api/df-reg/dfs')).json.find((x) => x.id === df.id);
  return d?.sts === 'CLOSED' ? d : null;
}, { tries: 45, every: 4000 });
expect(!!closed, 'the fiduciary becomes CLOSED', closed?.sts);
if (closed) {
  expect(!!closed.closedAt, 'with the time it closed');
  expect(closed.contactEmail === '[erased]', 'its contact address is erased', closed.contactEmail);
  expect(closed.name === name, 'its name is kept as the record of who it was');
}

const after = await reqs();
expect(after[given.id] === 'REVOKED' || after[given.id] === 'WITHDRAWN' || (after[given.id] && after[given.id] !== 'ISSUED'), 'its consent is ended', after[given.id]);
expect(after[waiting.id] === 'DENIED' || (after[waiting.id] && after[waiting.id] !== 'PENDING'), 'its waiting request is declined', after[waiting.id]);
expect(Object.keys(after).length === 2, 'and the records are still there', `${Object.keys(after).length} records`);

const refused = await call(key, 'GET', '/api/cns-cmd/prps-defs');
expect(refused.status === 401 && refused.json?.code === 'api-key-invalid', 'its API key is revoked, so refused as invalid', `${refused.status} ${refused.json?.code}`);

let signedIn = true;
try {
  await dfSignIn(contact);
} catch {
  signedIn = false;
}
expect(!signedIn, 'its team can no longer sign in (the accounts are deleted)');

// billing stopped with the consents (ADR-084): its plan stretch has an end, so no later month is invoiced
const billed = execFileSync('docker', ['exec', '-e', 'PGPASSWORD=cm', 'common-postgres-1', 'psql', '-U', 'cm', '-d', 'consent_manager', '-At', '-c',
  `select count(*) filter (where to_at is null), count(*) from bil.plan_hst where df_id = '${df.id}'`], { stdio: 'pipe' }).toString().trim();
expect(billed.startsWith('0|') && !billed.endsWith('|0'), 'its billing has ended (no open plan stretch left)', billed);

const resumeClosed = await api(staff, 'POST', `/api/df-reg/dfs/${df.id}/resume`);
expect(resumeClosed.status === 409, 'a closed fiduciary cannot be resumed', `${resumeClosed.status}`);

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
