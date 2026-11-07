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

// Suspending and resuming a fiduciary, end to end on the local stack (ADR-064). Suspends Security Test B Ltd:
//   - its API key is refused with df-suspended, and it can't raise a consent request
//   - its team can still sign in and read (they are told, not locked out)
//   - a fiduciary that isn't suspended is unaffected
//   - a reason is required, a pending or already suspended fiduciary can't be suspended, a fiduciary that isn't suspended can't be resumed
//   - resuming puts it back, and the same key works again
// It always resumes B, even when a check fails. Needs the keys file from setup-keys.mjs.
//
//   KEYS_FILE=/tmp/cm-keys.json node tests/security/e2e-suspension.mjs

import { GW, call, loadKeys } from './lib/cm1.mjs';

const KEYS = loadKeys(process.env.KEYS_FILE ?? (() => { throw new Error('Set KEYS_FILE to the file setup-keys.mjs wrote'); })());
const bearer = (t) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' });
const api = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: bearer(token), body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};

let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};

const staff = KEYS.staff;
const LIST = '/api/cns-cmd/prps-defs';
let suspended = false;
try {
  expect((await call(KEYS.b, 'GET', LIST)).status === 200, 'B\'s key works before');

  const noReason = await api(staff, 'POST', `/api/df-reg/dfs/${KEYS.dfB}/suspend`, {});
  expect(noReason.status === 400 && noReason.json.code === 'invalid-request', 'a reason is required', `${noReason.status} ${noReason.json?.code}`);

  const done = await api(staff, 'POST', `/api/df-reg/dfs/${KEYS.dfB}/suspend`, { reason: 'E2E test: suspension check' });
  suspended = done.status === 200;
  expect(done.status === 200 && done.json.sts === 'SUSPENDED' && done.json.suspensionReason === 'E2E test: suspension check', 'staff suspend B with a reason', `${done.status} ${done.json?.sts}`);
  const twice = await api(staff, 'POST', `/api/df-reg/dfs/${KEYS.dfB}/suspend`, { reason: 'again' });
  expect(twice.status === 409 && twice.json.code === 'df-cannot-suspend', 'suspending twice is refused', `${twice.status} ${twice.json?.code}`);

  const listed = (await api(staff, 'GET', '/api/df-reg/dfs')).json.find((d) => d.id === KEYS.dfB);
  expect(listed.sts === 'SUSPENDED' && listed.suspensionReason === 'E2E test: suspension check', 'the staff list shows it and why');

  const refused = await call(KEYS.b, 'GET', LIST);
  expect(refused.status === 403 && refused.json?.code === 'df-suspended', 'B\'s API key is refused', `${refused.status} ${refused.json?.code}`);
  const raise = await call(KEYS.b, 'POST', '/api/cns-cmd/cns-reqs', { body: { extDpRefTyp: 'EMAIL', extDpRefVal: 'perf-dp@example.test',
    prpsCode: 'ORDER_UPDATES', prpsVer: KEYS.prpsVerB } });
  expect(raise.status === 403, 'and it can\'t raise a consent request', `${raise.status}`);
  const team = await api(KEYS.tokenB, 'GET', '/api/df-reg/me/api-keys');
  expect(team.status === 200, 'B\'s team can still sign in and read', `${team.status}`);
  expect((await call(KEYS.a, 'GET', LIST)).status === 200, 'another fiduciary is unaffected');

  const resumed = await api(staff, 'POST', `/api/df-reg/dfs/${KEYS.dfB}/resume`);
  suspended = !(resumed.status === 200);
  expect(resumed.status === 200 && resumed.json.sts === 'ACTIVE' && !resumed.json.suspensionReason, 'staff resume B', `${resumed.status} ${resumed.json?.sts}`);
  const again = await api(staff, 'POST', `/api/df-reg/dfs/${KEYS.dfB}/resume`);
  expect(again.status === 409 && again.json.code === 'df-not-suspended', 'resuming an active fiduciary is refused', `${again.status} ${again.json?.code}`);
  expect((await call(KEYS.b, 'GET', LIST)).status === 200, 'the same key works again');
  const notStaff = await api(KEYS.tokenB, 'POST', `/api/df-reg/dfs/${KEYS.dfB}/suspend`, { reason: 'I want out' });
  expect(notStaff.status === 403, 'a fiduciary can\'t suspend itself or anyone', `${notStaff.status}`);
} finally {
  if (suspended) {
    await api(staff, 'POST', `/api/df-reg/dfs/${KEYS.dfB}/resume`);
    console.log('(B resumed after a failure)');
  }
}
console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
