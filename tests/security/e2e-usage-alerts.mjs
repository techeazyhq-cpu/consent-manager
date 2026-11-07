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

// Usage-threshold alerts end to end on the local stack (ADR-060). A fiduciary's monthly usage counter is set to 80% of
// its plan, then to 100%, and each crossing is checked: the alert is recorded once however many sweeps pass, the
// fiduciary's own API and CM Admin's both list it, and the email reaches the inbox with the right words. The counter
// is put back afterwards. Local stack only: it sets a Redis key and clears this month's alerts for the fiduciary.
//
//   node tests/security/e2e-usage-alerts.mjs        (takes about three minutes: the sweep runs every minute)

import { execFileSync } from 'node:child_process';
import { GW, MAILPIT, dfSignIn, staffSignIn } from './lib/cm1.mjs';

const REDIS = process.env.REDIS_CONTAINER || 'common-redis-1';
const POSTGRES = process.env.POSTGRES_CONTAINER || 'common-postgres-1';
const api = async (token, path) => {
  const res = await fetch(new URL(path, GW), { headers: { Authorization: `Bearer ${token}` } });
  return { status: res.status, json: res.status === 200 ? await res.json() : null };
};
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) {
    restore();
    process.exit(1);
  }
};
const until = async (fn, tries = 150) => {
  for (let i = 0; i < tries; i++) {
    const v = await fn();
    if (v) return v;
    await new Promise((ok) => setTimeout(ok, 1000));
  }
  return null;
};
const redis = (...args) => execFileSync('docker', ['exec', REDIS, 'redis-cli', ...args], { encoding: 'utf8' }).trim();
const psql = (sql) => execFileSync('docker', ['exec', POSTGRES, 'psql', '-U', 'cm', '-d', 'consent_manager', '-Atc', sql],
  { encoding: 'utf8' }).trim();

const startedAt = new Date(Date.now() - 2000);
const df = await dfSignIn(process.env.DF_A_OWNER || 'batch-owner@example.test');
const sub = (await api(df, '/api/bil/me/subscription')).json;
const dfId = JSON.parse(Buffer.from(df.split('.')[1], 'base64url')).df_id;
const key = `usg:${dfId}:${sub.usagePeriod}`;
const original = redis('get', key);
let restored = false;
function restore() {
  if (restored) return;
  restored = true;
  if (original) redis('set', key, original);
  else redis('del', key);
}
const quota = sub.reqPerMonthQta;
expect(quota > 0, 'the fiduciary has a monthly allowance', `${quota} on ${sub.subPlnName}`);

const name = (await api(df, `/api/df-reg/dfs/${dfId}`)).json?.name;
expect(!!name, 'the fiduciary has a name to find its emails by', name);
// only this fiduciary's: other tenants on the local stack may cross their own lines meanwhile
const mails = async (subject) => {
  const r = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`subject:"${subject}"`)}&limit=50`)).json();
  return (r.messages ?? []).filter((m) => new Date(m.Created) >= startedAt && m.Snippet.includes(name));
};
const ownAlerts = async () => (await api(df, '/api/bil/me/usage-alerts')).json ?? [];

// a clean month for this fiduciary, and a counter under 80%
psql(`delete from bil.usg_alert where df_id = '${dfId}' and period = '${sub.usagePeriod}'`);
redis('set', key, String(Math.floor(quota * 0.5)));
await new Promise((ok) => setTimeout(ok, 70_000));
expect((await ownAlerts()).filter((a) => a.period === sub.usagePeriod).length === 0, 'at 50% there is no alert');

// 80%
redis('set', key, String(Math.ceil(quota * 0.8)));
const eighty = await until(async () => (await ownAlerts()).find((a) => a.period === sub.usagePeriod && a.threshold === 80));
expect(!!eighty, 'at 80% an alert is recorded within a sweep', eighty && `${eighty.used} of ${eighty.quota}`);
expect(eighty.planName === sub.subPlnName && eighty.quota === quota, 'it records the plan and allowance at the time');
const mail80 = await until(async () => (await mails("used 80% of this month's consent request allowance")).length >= 1, 60);
expect(!!mail80, "the fiduciary's contact address is emailed at 80%");
const body80 = await (await fetch(`${MAILPIT}/api/v1/message/${(await mails("used 80% of this month's"))[0].ID}`)).json();
expect(body80.Text.includes(`of its ${quota} consent requests`) && body80.Text.includes('ask for a larger plan'),
  'the email says how much, and what to do');

// no second alert or email however many sweeps pass
await new Promise((ok) => setTimeout(ok, 130_000));
expect((await ownAlerts()).filter((a) => a.period === sub.usagePeriod && a.threshold === 80).length === 1,
  'two more sweeps raise nothing more at 80%');
expect((await mails("used 80% of this month's")).length === 1, 'and send no second email');

// 100%
redis('set', key, String(quota));
const full = await until(async () => (await ownAlerts()).find((a) => a.period === sub.usagePeriod && a.threshold === 100));
expect(!!full, 'at 100% a second alert is recorded', full && `${full.used} of ${full.quota}`);
const mail100 = await until(async () => (await mails('allowance for ' + sub.usagePeriod + ' is used up')).length >= 1, 60);
expect(!!mail100, 'and the email says the allowance is used up');

// CM Admin sees them
const staff = await staffSignIn(process.env.STAFF || 'operator@cm.local');
const all = (await api(staff, '/api/bil/usage-alerts')).json ?? [];
expect(all.filter((a) => a.dfId === dfId).length === 2, "CM Admin's list has both for the fiduciary");
const bad = await api(staff, '/api/bil/usage-alerts?period=2026-13');
expect(bad.status === 400, 'a bad period is 400', bad.status);
const dfStaffList = await api(df, '/api/bil/usage-alerts');
expect(dfStaffList.status === 403, "a fiduciary can't list everyone's alerts", dfStaffList.status);

restore();
console.log(`All checks passed. Fiduciary ${dfId}; the usage counter is back to ${original ?? '(unset)'}.`);
