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

// The platform's public status, end to end on the local stack (ADR-067):
//   - anyone can read it, with no sign-in, and it names what a customer can understand and nothing of how it is built
//   - stopping a service (billing, a non-critical one) shows it as down within about a minute and the platform as degraded,
//     and starting it again clears that
//   - staff post a notice and resolve it; it stays visible after, and only staff can post
// It stops cm-bil-svc for about two minutes (docker) and starts it again, even when a check fails.
//
//   node tests/security/e2e-status.mjs

import { execFileSync } from 'node:child_process';
import { GW, KC } from './lib/cm1.mjs';

let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const status = async () => {
  const res = await fetch(`${GW}/api/status`);
  const text = await res.text();
  return { code: res.status, text, json: JSON.parse(text) };
};
const until = async (what, test, { tries = 40, every = 4000 } = {}) => {
  for (let i = 0; i < tries; i++) {
    const s = await status();
    if (test(s.json)) return s.json;
    await sleep(every);
  }
  console.log(`(gave up waiting for ${what})`);
  return null;
};
const docker = (...args) => execFileSync('docker', args, { stdio: 'pipe' }).toString();
const staff = (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local', password: 'admin' }) })).json()).access_token;
const post = async (path, body, token) => {
  const res = await fetch(`${GW}${path}`, { method: 'POST', headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: JSON.stringify(body ?? {}) });
  const text = await res.text();
  return { code: res.status, json: text ? JSON.parse(text) : undefined };
};

// anyone can read it
const first = await status();
expect(first.code === 200, 'anyone can read the status, with no sign-in', `${first.code}`);
const ids = first.json.components.map((c) => c.id);
expect(['cns-cmd', 'cns-qry', 'df-reg', 'sign-in', 'bil'].every((id) => ids.includes(id)), 'it lists the parts of the platform', ids.join(', '));
expect(first.json.components.every((c) => c.label && !/[a-z]+-svc/.test(c.label)), 'in words a customer understands');
expect(!/localhost|actuator|:80\d\d|http:|\bsvc\b|version/i.test(first.text), 'and says nothing of how it is built: no address, port, endpoint or version');

// a service goes down and comes back
try {
  const before = await until('everything to be operational', (r) => r.overall === 'OPERATIONAL', { tries: 30, every: 4000 });
  expect(!!before, 'to begin with the platform is operational', before?.overall);
  docker('stop', 'infra-cm-bil-svc-1');
  console.log('(billing stopped; waiting for the status page to notice, up to about two minutes)');
  const down = await until('billing to show as down', (r) => r.components.find((c) => c.id === 'bil')?.state === 'OUTAGE', { tries: 30, every: 4000 });
  expect(!!down, 'a stopped service shows as down', down?.components.find((c) => c.id === 'bil')?.state);
  expect(down?.overall === 'DEGRADED', 'and the platform as degraded (billing is not critical)', down?.overall);
  const bil = down?.components.find((c) => c.id === 'bil');
  expect(bil && bil.uptime24h !== null && bil.uptime24h < 100, 'its uptime reflects it', `${bil?.uptime24h}%`);
  const others = down?.components.filter((c) => c.id !== 'bil' && ['cns-cmd', 'cns-qry', 'df-reg'].includes(c.id)) ?? [];
  expect(others.length === 3 && others.every((c) => c.state === 'OPERATIONAL'), 'the others still show as operational');
} finally {
  docker('start', 'infra-cm-bil-svc-1');
}
const back = await until('billing to recover', (r) => r.components.find((c) => c.id === 'bil')?.state === 'OPERATIONAL' && r.overall === 'OPERATIONAL', { tries: 40, every: 4000 });
expect(!!back, 'started again, it shows as operational and the platform as operational', back?.overall);

// notices
const noReason = await post('/api/status/notices', { title: '', severity: 'incident' }, staff);
expect(noReason.code === 400 && noReason.json.code === 'invalid-request', 'a notice needs a title', `${noReason.code} ${noReason.json?.code}`);
const badSeverity = await post('/api/status/notices', { title: 'x', severity: 'catastrophe' }, staff);
expect(badSeverity.code === 400, 'and a known kind', `${badSeverity.code}`);
const badComponent = await post('/api/status/notices', { title: 'x', severity: 'info', componentIds: ['the-database'] }, staff);
expect(badComponent.code === 400, 'and only components the page shows', `${badComponent.code}`);
const anonymous = await post('/api/status/notices', { title: 'Spam', severity: 'incident' });
expect(anonymous.code === 401, 'nobody signed out can post', `${anonymous.code}`);

const title = `E2E test notice ${Date.now()}`;
const posted = await post('/api/status/notices', { title, body: 'Planned work on notifications.', severity: 'maintenance', componentIds: ['ntf'] }, staff);
expect(posted.code === 201 && posted.json.severity === 'MAINTENANCE', 'staff post a notice', `${posted.code}`);
const shown = (await until('the notice to show', (r) => r.notices.some((n) => n.title === title), { tries: 6, every: 3000 }));
expect(!!shown, 'it is on the public page');
const resolved = await post(`/api/status/notices/${posted.json.id}/resolve`, {}, staff);
expect(resolved.code === 200 && !!resolved.json.resolvedAt, 'staff resolve it', `${resolved.code}`);
const after = await until('the resolved notice', (r) => r.notices.some((n) => n.title === title && n.resolvedAt), { tries: 6, every: 3000 });
expect(!!after, 'and it stays on the page, marked resolved');
const twice = await post(`/api/status/notices/${posted.json.id}/resolve`, {}, staff);
expect(twice.code === 400, 'a resolved notice cannot be resolved again', `${twice.code}`);

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
