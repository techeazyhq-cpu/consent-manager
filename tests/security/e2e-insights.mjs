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

// The dashboard's figures (ADR-049, ADR-078) on the local stack, as the Owner of Batch Test Co sees them: the default answer is in
// UTC with no previous window; a time zone cuts the days there; `compare` adds the window just before it; a bad window or zone is
// refused; the CSV export carries the same days and purposes.
//
//   node tests/security/e2e-insights.mjs

import { GW, dfSignIn } from './lib/cm1.mjs';

let failed = false;
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) failed = true;
};
const get = async (token, path) => {
  const res = await fetch(`${GW}${path}`, { headers: { Authorization: `Bearer ${token}` } });
  return { status: res.status, type: res.headers.get('content-type') ?? '', disposition: res.headers.get('content-disposition') ?? '', text: await res.text() };
};

const owner = await dfSignIn('batch-owner@example.test');

const plain = await get(owner, '/api/cns-qry/insights?days=7');
const base = JSON.parse(plain.text);
expect(plain.status === 200 && base.zone === 'UTC' && !base.previous && base.days.length >= 7, 'by default: UTC, no previous window, a row for every day', `${plain.status} ${base.zone} ${base.days.length} days`);

const india = JSON.parse((await get(owner, '/api/cns-qry/insights?days=7&tz=Asia/Kolkata')).text);
expect(india.zone === 'Asia/Kolkata' && india.days.length >= 7, 'a time zone cuts the days there', `${india.zone}`);
expect(india.outcomes.raised === base.outcomes.raised, 'and does not change the totals');

const compared = JSON.parse((await get(owner, '/api/cns-qry/insights?days=7&compare=true')).text);
const prev = compared.previous;
expect(Boolean(prev) && prev.to === compared.from && Date.parse(compared.from) - Date.parse(prev.from) === 7 * 86_400_000, 'compare adds the 7 days just before', prev ? `${prev.from} to ${prev.to}` : 'none');
expect(typeof prev.outcomes.raised === 'number' && typeof prev.decisions.decided === 'number' && typeof prev.changes.withdrawn === 'number', 'with outcomes, decisions and changes');

expect((await get(owner, '/api/cns-qry/insights?days=7&tz=Mars/Olympus')).status === 400, 'an unknown time zone is refused');
expect((await get(owner, '/api/cns-qry/insights?days=lots')).status === 400, 'a window that is not a number is refused');

const days = await get(owner, '/api/cns-qry/insights/export?days=7&view=days');
const rows = days.text.trim().split('\n');
expect(days.status === 200 && days.type.startsWith('text/csv') && days.disposition.includes('consent-insights-days.csv'), 'the days export is a CSV file', `${days.status} ${days.type}`);
expect(rows[0] === 'date,raised,given,refused' && rows.length - 1 === base.days.length, 'with a header and one row per day', `${rows.length - 1} rows`);

const purposes = await get(owner, '/api/cns-qry/insights/export?days=7&view=purposes');
expect(purposes.status === 200 && purposes.text.startsWith('purpose,raised,given,refused,awaiting,not_notified,median_seconds_to_decide\n'), 'the purposes export has its own header');
expect((await get(owner, '/api/cns-qry/insights/export?days=7&view=everything')).status === 400, 'an unknown view is refused');

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
