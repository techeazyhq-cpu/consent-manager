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

// Invoicing (ADR-079) on the local stack. A throwaway fiduciary is put on STANDARD, the plan's price is set, and the month that has
// just ended is invoiced; the run is repeated (nothing new), the fiduciary reads its invoice (and nobody else's), staff mark it
// paid, credit it once, and read the accounting export. Plan prices are restored afterwards. Needs the stack running; the plan
// history of a fiduciary created today starts today, so the invoice is for the part of the month it existed: the check that matters
// is that the amounts follow the history, not that they are round.
//
//   node tests/security/e2e-invoicing.mjs

import { randomBytes } from 'node:crypto';
import { GW, KC, dfSignIn } from './lib/cm1.mjs';

let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const api = async (token, method, path, body) => {
  const res = await fetch(`${GW}${path}`, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const text = await res.text();
  let json;
  try { json = text ? JSON.parse(text) : undefined; } catch { json = undefined; }
  return { status: res.status, json, text };
};
const staff = async (username, password) => (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username, password }) })).json()).access_token;

const admin = await staff('admin@cm.local', 'admin');
const operator = await staff('operator@cm.local', 'operator');

// a fiduciary whose first plan stretch began in the last month: backdate its history, as a real one would have
const tag = randomBytes(4).toString('hex');
const contact = `invoicing-${tag}@invoicing.example.test`;
const df = (await api(admin, 'POST', '/api/df-reg/dfs', { name: `Invoicing Test ${tag}`, contactEmail: contact })).json;
await api(admin, 'POST', `/api/df-reg/dfs/${df.id}/activate`);
const owner = await dfSignIn(contact);
const other = await dfSignIn('contact@security-b.example.test');
await api(admin, 'PUT', `/api/bil/dfs/${df.id}/sub-pln`, { subPlnName: 'STANDARD' });

const now = new Date();
const prevMonth = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1));
const month = `${prevMonth.getUTCFullYear()}-${String(prevMonth.getUTCMonth() + 1).padStart(2, '0')}`;

const prices = (await api(admin, 'GET', '/api/bil/plan-prices')).json;
const was = Object.fromEntries(prices.map((p) => [p.planName, p.monthlyMinor]));
expect(prices.some((p) => p.planName === 'STANDARD'), 'staff read the plan prices', JSON.stringify(was));

const set = await api(admin, 'PUT', '/api/bil/plan-prices/STANDARD', { monthlyMinor: 300000 });
expect(set.status === 200 && set.json.monthlyMinor === 300000, 'an administrator sets the STANDARD price', `${set.status}`);
expect((await api(operator, 'PUT', '/api/bil/plan-prices/STANDARD', { monthlyMinor: 1 })).status === 403, 'an operator cannot');
expect((await api(admin, 'PUT', '/api/bil/plan-prices/STANDARD', { monthlyMinor: -5 })).json?.code === 'invalid-price', 'a negative price is refused');

try {
  expect((await api(admin, 'POST', `/api/bil/invoices/run?month=${now.getUTCFullYear()}-${String(now.getUTCMonth() + 1).padStart(2, '0')}`)).json?.code === 'billing-period-open', 'the month in progress cannot be invoiced');
  expect((await api(admin, 'POST', '/api/bil/invoices/run?month=soon')).json?.code === 'invalid-month', 'a bad month is refused');

  // the fiduciary's history starts now: stand its stretch back to the start of last month, so the month has something to bill
  // the id came back from an API, so it is checked to be a UUID, and the command is run without a shell, with the statement as one argument
  if (!/^[0-9a-f-]{36}$/.test(df.id)) throw new Error('the new fiduciary id is not a UUID');
  const { execFileSync } = await import('node:child_process');
  const sql = `update bil.plan_hst set from_at = '${prevMonth.toISOString()}' where df_id = '${df.id}' and to_at is null`;
  execFileSync('docker', ['exec', '-e', 'PGPASSWORD=cm', 'common-postgres-1', 'psql', '-U', 'cm', '-d', 'consent_manager', '-c', sql], { stdio: 'pipe' });

  const run = await api(admin, 'POST', `/api/bil/invoices/run?month=${month}`);
  const mine = (run.json ?? []).find((i) => i.fiduciaryId === df.id);
  expect(run.status === 200 && Boolean(mine), 'running the month issues an invoice to the fiduciary', `${run.status} ${mine?.number}`);
  expect(mine.kind === 'INVOICE' && mine.status === 'ISSUED' && mine.subtotalMinor === 300000 && mine.lines.length === 1, 'one line, the whole month at the plan price', `${mine.subtotalMinor} ${mine.lines[0]?.description}`);
  expect(/^INV-\d{4}-\d{6}$/.test(mine.number), 'with a sequential number', mine.number);

  const again = await api(admin, 'POST', `/api/bil/invoices/run?month=${month}`);
  expect(!(again.json ?? []).some((i) => i.fiduciaryId === df.id), 'running it again issues nothing more for the fiduciary');

  const own = await api(owner, 'GET', '/api/bil/me/invoices');
  expect(own.status === 200 && own.json.length === 1 && own.json[0].id === mine.id, 'the fiduciary reads its invoice', `${own.json.length}`);
  expect((await api(other, 'GET', `/api/bil/me/invoices/${mine.id}`)).status === 404, 'another fiduciary cannot read it');
  const html = await fetch(`${GW}/api/bil/me/invoices/${mine.id}/html`, { headers: { Authorization: `Bearer ${owner}` } });
  const page = await html.text();
  expect(html.status === 200 && html.headers.get('content-type').startsWith('text/html') && page.includes(mine.number) && page.includes('3000.00'), 'and a printable page', `${html.status}`);

  const paid = await api(admin, 'POST', `/api/bil/invoices/${mine.id}/paid`, { note: 'bank transfer 123' });
  expect(paid.status === 200 && paid.json.status === 'PAID', 'staff mark it paid', `${paid.status}`);
  expect((await api(admin, 'POST', `/api/bil/invoices/${mine.id}/paid`, {})).json?.code === 'invoice-not-payable', 'and not twice');

  expect((await api(admin, 'POST', `/api/bil/invoices/${mine.id}/credit-note`, {})).json?.code === 'invalid-credit-reason', 'a credit note needs a reason');
  const credit = await api(admin, 'POST', `/api/bil/invoices/${mine.id}/credit-note`, { reason: 'billed in error' });
  expect(credit.status === 201 && credit.json.kind === 'CREDIT_NOTE' && credit.json.totalMinor === -300000 && /^CN-/.test(credit.json.number), 'an administrator credits it with a credit note of its own number', `${credit.status} ${credit.json?.number}`);
  expect((await api(admin, 'POST', `/api/bil/invoices/${mine.id}/credit-note`, { reason: 'again' })).json?.code === 'invoice-already-credited', 'once');
  const after = (await api(owner, 'GET', `/api/bil/me/invoices/${mine.id}`)).json;
  expect(after.status === 'CREDITED', 'the invoice now says credited');

  const list = await api(admin, 'GET', `/api/bil/invoices?df=${df.id}`);
  expect(list.json.length === 2, 'staff see both documents', `${list.json.length}`);
  const csv = await fetch(`${GW}/api/bil/invoices/export?month=${month}`, { headers: { Authorization: `Bearer ${admin}` } });
  const rows = (await csv.text()).split('\n');
  expect(csv.status === 200 && rows[0].startsWith('number,kind,fiduciary') && rows.some((r) => r.includes(mine.number) && r.includes('3000.00')), 'the accounting export has a row for each', `${rows.length - 1} rows`);
  expect((await api(operator, 'POST', `/api/bil/invoices/${mine.id}/paid`, {})).status === 403, 'an operator reads invoices but does not change them');
} finally {
  // put the prices back
  for (const [planName, monthlyMinor] of Object.entries(was)) await api(admin, 'PUT', `/api/bil/plan-prices/${planName}`, { monthlyMinor });
}

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
