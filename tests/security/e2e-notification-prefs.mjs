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

// How a principal hears of new consent requests, end to end on the local stack (ADR-066), with a throwaway principal:
//   - a request addressed to their email reaches them by email, as ever
//   - they start with push on and request emails on, and the address is shown masked
//   - turning request emails off stops the next request being mailed to that address (it waits on their dashboard)
//   - turning them on again mails the next one; pausing push is remembered
//   - the choice is theirs alone: staff and anonymous callers have none, and a dpId in a body is ignored
// Needs the keys file from setup-keys.mjs (the fiduciary that raises the requests).
//
//   KEYS_FILE=/tmp/cm-keys.json node tests/security/e2e-notification-prefs.mjs

import { randomBytes } from 'node:crypto';
import { GW, MAILPIT, call, dpSignIn, loadKeys } from './lib/cm1.mjs';

const KEYS = loadKeys(process.env.KEYS_FILE ?? (() => { throw new Error('Set KEYS_FILE to the file setup-keys.mjs wrote'); })());
let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const bearer = (t) => ({ Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' });
const api = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: token ? bearer(token) : {}, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  return { status: res.status, json: text ? JSON.parse(text) : undefined };
};
const count = async (address) => {
  const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${address}`)}`)).json();
  return found.messages_count ?? found.total ?? 0;
};
/** Waits for the count to reach {@code atLeast}, or for {@code seconds} to pass; returns the count. */
const countUntil = async (address, atLeast, seconds) => {
  for (let i = 0; i < seconds; i++) {
    const n = await count(address);
    if (n >= atLeast) return n;
    await sleep(1000);
  }
  return count(address);
};

const tag = randomBytes(4).toString('hex');
const dp = `prefs-${tag}@prefs.example.test`;
const raise = () => call(KEYS.a, 'POST', '/api/cns-cmd/cns-reqs', { body: { extDpRefTyp: 'EMAIL', extDpRefVal: dp, prpsCode: 'ORDER_UPDATES', prpsVer: KEYS.prpsVerA } });

// as ever: a request addressed to an email reaches it
expect((await raise()).status === 201, 'an organisation raises a request to the principal\'s email');
let seen = await countUntil(dp, 1, 10);
expect(seen === 1, 'and it reaches them by email', `${seen} message(s)`);

// signing in (which also sends a code), then the preferences
const token = await dpSignIn(dp);
await api(token, 'POST', '/api/cns-cmd/dp-links', { extDpRefTyp: 'EMAIL', extDpRefVal: dp });
seen = await count(dp); // the request and the sign-in code
const start = await api(token, 'GET', '/api/ntf/preferences');
expect(start.status === 200 && start.json.push === true && start.json.email === true, 'they start with push and request emails on', JSON.stringify({ push: start.json?.push, email: start.json?.email }));
expect(start.json.canEmail === true && start.json.emailAddress?.includes('•••') && !start.json.emailAddress.includes(dp), 'and their address is shown masked');

// request emails off
const off = await api(token, 'PUT', '/api/ntf/preferences', { push: true, email: false });
expect(off.status === 200 && off.json.email === false, 'they turn request emails off', `${off.status}`);
const quiet = await raise();
await sleep(7000);
expect(quiet.status === 201 && await count(dp) === seen, 'the next request is not mailed to them', `${await count(dp)} message(s), as before`);
// opening the portal claims what was addressed to them (as the portal does on load), and the request is there to decide
let pending = 0;
for (let i = 0; i < 10 && pending < 2; i++) {
  await api(token, 'POST', '/api/cns-cmd/dp-links', { extDpRefTyp: 'EMAIL', extDpRefVal: dp });
  const dash = await api(token, 'GET', '/api/cns-qry/dp-dashboard');
  pending = (Array.isArray(dash.json) ? dash.json : []).reduce((sum, row) => sum + (row.pendingCnt ?? 0), 0);
  if (pending < 2) await sleep(1000);
}
expect(pending >= 2, 'both requests, the mailed one and the one that was not, wait on their dashboard', `${pending} pending`);

// on again
const on = await api(token, 'PUT', '/api/ntf/preferences', { push: false, email: true });
expect(on.status === 200 && on.json.email === true && on.json.push === false, 'they turn request emails back on and pause push', `${on.status}`);
expect((await raise()).status === 201, 'another request is raised');
const back = await countUntil(dp, seen + 1, 10);
expect(back === seen + 1, 'it is mailed again', `${back} message(s)`);
const remembered = await api(token, 'GET', '/api/ntf/preferences');
expect(remembered.json.push === false && remembered.json.email === true, 'push stays paused');

// the choice is theirs alone
expect((await api(KEYS.staff, 'GET', '/api/ntf/preferences')).status === 403, 'platform staff have none');
expect((await api(null, 'GET', '/api/ntf/preferences')).status === 401, 'nor does anyone not signed in');
const sneaky = await api(token, 'PUT', '/api/ntf/preferences', { push: false, email: true, dpId: 'someone-else' });
expect(sneaky.status === 200, 'a dpId in the body is ignored: the caller is always the token');

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
