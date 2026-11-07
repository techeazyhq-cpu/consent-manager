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

// Principal requests end to end on the local stack (ADR-058): a fiduciary asks a new principal for consent; the
// principal makes a request about it; the fiduciary's webhook gets principal_request.received without the principal's
// words; the fiduciary refuses it citing a legal ground; another fiduciary can't see it; a grievance reaches platform
// staff, who answer it. Exits non-zero on the first failure.
//
//   node tests/security/e2e-dsr.mjs

import { randomBytes } from 'node:crypto';
import { GW, dfSignIn, dpSignIn, staffSignIn } from './lib/cm1.mjs';

const api = async (token, method, path, body) => {
  const res = await fetch(new URL(path, GW), { method, headers: { Authorization: `Bearer ${token}`,
    ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not JSON */ }
  return { status: res.status, json, text };
};
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail ? `  ->  ${detail}` : ''}`);
  if (!ok) process.exit(1);
};
const until = async (fn, tries = 30) => {
  for (let i = 0; i < tries; i++) {
    const v = await fn();
    if (v) return v;
    await new Promise((ok) => setTimeout(ok, 1000));
  }
  return null;
};

const ownerA = process.env.DF_A_OWNER || 'batch-owner@example.test';
const ownerB = process.env.DF_B_OWNER || 'owner-b@example.test';
const principal = `dsr-${randomBytes(3).toString('hex')}@example.test`;

const dfA = await dfSignIn(ownerA);
// a published purpose to ask for
const purposes = (await api(dfA, 'GET', '/api/cns-cmd/prps-defs')).json ?? [];
const prps = purposes.find((p) => p.currentVer);
expect(!!prps, 'fiduciary A has a published purpose', prps?.code);

// a webhook endpoint for the new types (the local stack allows http)
const endpoint = await api(dfA, 'POST', '/api/cns-cmd/webhooks/endpoints', { url: 'http://cm-gw:9000/actuator/health',
  description: 'e2e-dsr', types: ['principal_request.received', 'principal_request.withdrawn'] });
expect(endpoint.status === 201, 'A subscribes a webhook to principal_request.*', `${endpoint.status} ${endpoint.json?.code ?? ''}`);

const raised = await api(dfA, 'POST', '/api/cns-cmd/cns-reqs', { extDpRefTyp: 'EMAIL', extDpRefVal: principal,
  prpsCode: prps.code, prpsVer: prps.currentVer });
expect(raised.status === 201, 'A asks a new principal for consent', `${raised.status} ${raised.json?.code ?? ''}`);
const cnsReqId = raised.json.id;

const dp = await dpSignIn(principal);
await api(dp, 'POST', '/api/cns-cmd/dp-links', { extDpRefTyp: 'EMAIL', extDpRefVal: principal });
const mine = await until(async () => ((await api(dp, 'GET', '/api/cns-qry/cns-arts')).json ?? [])
  .find((r) => r.cnsReqId === cnsReqId));
expect(!!mine, 'the principal sees the consent request', mine?.lcySts);

// ---- the principal asks A to erase, in their own words
const words = `Please erase everything, ref ${randomBytes(4).toString('hex')}`;
const req = await api(dp, 'POST', '/api/dsr/me/requests', { kind: 'RELAY', cnsReqId, right: 'ERASURE', details: words });
expect(req.status === 201 && req.json.dfId === mine.dfId && req.json.sts === 'OPEN', 'the principal makes a request',
  `${req.status} ${req.json?.dueBy ?? req.text}`);
const again = await api(dp, 'POST', '/api/dsr/me/requests', { kind: 'RELAY', cnsReqId, right: 'ERASURE' });
expect(again.status === 409, 'the same request twice is 409', `${again.status} ${again.json?.code ?? ''}`);
const notMine = await api(dp, 'POST', '/api/dsr/me/requests', { kind: 'RELAY', cnsReqId: '00000000-0000-0000-0000-000000000000',
  right: 'ACCESS' });
expect(notMine.status === 404, "someone else's or no consent is 404", `${notMine.status}`);

// ---- A's webhook is told, without the principal's words
const delivery = await until(async () => ((await api(dfA, 'GET',
  `/api/cns-cmd/webhooks/deliveries?endpointId=${endpoint.json.endpoint.id}`)).json ?? []).find((d) => d.type === 'principal_request.received'));
expect(!!delivery, "A's webhook gets principal_request.received", delivery?.sts);
const detail = (await api(dfA, 'GET', `/api/cns-cmd/webhooks/deliveries/${delivery.id}`)).json;
const payload = JSON.stringify(detail);
expect(payload.includes(req.json.id) && !payload.includes(words) && !payload.includes(principal),
  "the message has the request's id, not the principal's words or email");

// ---- A sees and answers it; B can't
const inbox = (await api(dfA, 'GET', '/api/dsr/df/requests?sts=OPEN')).json ?? [];
expect(inbox.some((r) => r.id === req.json.id && r.details === words), "A's inbox has it, with the principal's words");
const refuseNoGround = await api(dfA, 'POST', `/api/dsr/df/requests/${req.json.id}/refuse`, { response: 'No' });
expect(refuseNoGround.status === 400, 'a refusal without a legal ground is 400', `${refuseNoGround.status}`);
let dfB = null;
try { dfB = await dfSignIn(ownerB); } catch (e) { console.log(`SKIP  fiduciary B (${e.message})`); }
if (dfB) {
  const peek = await api(dfB, 'GET', `/api/dsr/df/requests/${req.json.id}`);
  expect(peek.status === 404, "B can't read A's request", `${peek.status}`);
}
const refused = await api(dfA, 'POST', `/api/dsr/df/requests/${req.json.id}/refuse`,
  { response: 'We must keep invoices for eight years.', ground: 'Income-tax Act, 1961 s.44AA retention' });
expect(refused.status === 200 && refused.json.sts === 'REFUSED', 'A refuses, citing its ground', refused.json?.answeredBy);
const seen = ((await api(dp, 'GET', '/api/dsr/me/requests')).json ?? []).find((r) => r.id === req.json.id);
expect(seen?.sts === 'REFUSED' && seen.refusalGround?.startsWith('Income-tax'), 'the principal sees the refusal and its ground');

// ---- a grievance about the platform, answered by staff
const g = await api(dp, 'POST', '/api/dsr/me/requests', { kind: 'GRIEVANCE', details: 'I got the code twice' });
expect(g.status === 201 && g.json.kind === 'GRIEVANCE', 'the principal complains about the platform', `${g.status}`);
const dfPeek = await api(dfA, 'GET', '/api/dsr/grievances');
expect(dfPeek.status === 403, "a fiduciary can't list grievances", `${dfPeek.status}`);
const staff = await staffSignIn(process.env.STAFF || 'operator@cm.local');
{
  const queue = (await api(staff, 'GET', '/api/dsr/grievances?sts=OPEN')).json ?? [];
  expect(queue.some((r) => r.id === g.json.id), "staff see the grievance");
  const ans = await api(staff, 'POST', `/api/dsr/grievances/${g.json.id}/resolve`, { response: 'Fixed in the next release.' });
  expect(ans.status === 200 && ans.json.sts === 'RESOLVED', 'staff answer it', ans.json?.answeredBy);
}
await api(dfA, 'DELETE', `/api/cns-cmd/webhooks/endpoints/${endpoint.json.endpoint.id}`);
console.log('All checks passed.');
