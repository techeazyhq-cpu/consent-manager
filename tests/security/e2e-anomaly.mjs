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

// Unusual activity (ADR-112), end to end on the local stack, with a fiduciary that has history (Batch Test Co):
//   - a burst of API keys issued in one hour is found once that hour is complete, as an open finding for that fiduciary
//   - a fiduciary's own owner cannot read the findings (platform administrators only)
//   - an administrator acknowledges it, and it leaves the open list
//
// The look is at the last COMPLETE hour, so this waits for the clock to pass the hour (up to about an hour). It needs the audit service
// started with CM_ANOMALY_INTERVAL_MS=60000 and CM_ANOMALY_MIN_HISTORY_HOURS=1 so it looks every minute and the test fiduciary has history enough.
//
//   node tests/security/e2e-anomaly.mjs

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

const staff = (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local', password: 'admin' }) })).json()).access_token;
const owner = await dfSignIn('batch-owner@example.test');
const me = (await api(owner, 'GET', '/api/df-reg/me')).json;
const dfId = me?.id ?? me?.dfId;

const startOfThisHour = Math.floor(Date.now() / 3600000) * 3600000;
const keys = [];
for (let i = 0; i < 30; i++) {
  const issued = await api(owner, 'POST', '/api/df-reg/me/api-keys', { name: `Anomaly test ${i}`, signingRequired: false });
  if (issued.status === 201 || issued.status === 200) keys.push(issued.json.id ?? issued.json.keyId);
}
expect(keys.length === 30, '30 API keys were issued in the hour', `${keys.length}`);
for (const id of keys) await api(owner, 'DELETE', `/api/df-reg/me/api-keys/${id}`);

const waitMs = startOfThisHour + 3600000 - Date.now();
console.log(`      the hour ends in ${Math.round(waitMs / 60000)} minutes; the detector looks at it after that`);
const deadline = startOfThisHour + 3600000 + 6 * 60000;
let found;
while (Date.now() < deadline) {
  await new Promise((r) => setTimeout(r, 30000));
  const list = await api(staff, 'GET', '/api/aud/anomalies?status=OPEN');
  found = list.json?.find?.((a) => a.signal === 'api-keys-issued' && new Date(a.windowStart).getTime() === startOfThisHour);
  if (found) break;
}
expect(!!found, 'the burst is an open finding for api-keys-issued in that hour', found ? `${found.observed} against about ${found.expected}, ${found.score} spreads` : 'none');
if (found) {
  expect(!!dfId ? found.dfId === dfId : true, 'for the fiduciary that did it', found.dfId);
  expect(JSON.stringify(found).match(/@/) === null, 'and it holds nothing like an address');
  const asOwner = await api(owner, 'GET', '/api/aud/anomalies');
  expect(asOwner.status === 403, 'a fiduciary cannot read the findings', `${asOwner.status}`);
  const ack = await api(staff, 'POST', `/api/aud/anomalies/${found.id}/acknowledge`);
  expect(ack.status === 204, 'an administrator acknowledges it', `${ack.status}`);
  const open = await api(staff, 'GET', '/api/aud/anomalies?status=OPEN');
  expect(!open.json.some((a) => a.id === found.id), 'and it leaves the open list');
  const again = await api(staff, 'POST', `/api/aud/anomalies/${found.id}/acknowledge`);
  expect(again.status === 404 && again.json?.code === 'anomaly-not-found', 'acknowledging again is a 404', `${again.status}`);
}

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
