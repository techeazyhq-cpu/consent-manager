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

// ---- calendar months (ADR-088)
const ym = (back) => {
  const now = new Date();
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, '0')}`;
};
const ms = (iso) => Date.parse(iso);
const thisM = JSON.parse((await get(owner, `/api/cns-qry/insights?month=${ym(0)}&compare=true`)).text);
expect(ms(thisM.from) === ms(`${ym(0)}-01T00:00:00Z`) && ms(thisM.to) > ms(thisM.from) && ms(thisM.previous.from) === ms(`${ym(1)}-01T00:00:00Z`), 'this month runs from its first day to now, and is set against the month before', `${thisM.from} .. ${thisM.to}`);
expect(ms(thisM.previous.to) - ms(thisM.previous.from) === Math.min(ms(thisM.to) - ms(thisM.from), ms(thisM.from) - ms(thisM.previous.from)), 'against the same stretch of that month\'s start, never more than the month');

const lastM = JSON.parse((await get(owner, `/api/cns-qry/insights?month=${ym(1)}&compare=true&tz=Asia/Kolkata`)).text);
expect(lastM.zone === 'Asia/Kolkata' && ms(lastM.previous.to) === ms(lastM.from), 'a month that has ended is set against all of the one before it, in the zone asked for', `${lastM.from} .. ${lastM.to}`);
expect(ms(lastM.from) === ms(`${ym(1)}-01T00:00:00Z`) - 5.5 * 3600_000, 'its first day begins at midnight in India, 5 h 30 min before midnight UTC');
expect(ms(lastM.to) === ms(`${ym(0)}-01T00:00:00Z`) - 5.5 * 3600_000, 'and it ends where the next month begins there');

expect((await get(owner, `/api/cns-qry/insights?month=${ym(-1)}`)).status === 400, 'a month in the future is refused');
expect((await get(owner, `/api/cns-qry/insights?month=${ym(30)}`)).status === 400, 'and one more than 24 months back');
expect((await get(owner, '/api/cns-qry/insights?month=october')).status === 400, 'and one that is not yyyy-MM');
const monthCsv = await get(owner, `/api/cns-qry/insights/export?month=${ym(1)}&view=days`);
const monthRows = monthCsv.text.trim().split('\n');
expect(monthCsv.status === 200 && monthRows.length - 1 >= 28, 'the CSV of a month has a row for each of its days', `${monthRows.length - 1} rows`);
expect((await get(owner, `/api/cns-qry/insights/export?month=${ym(-1)}&view=days`)).status === 400, 'and a month in the future is refused there too');

// ---- calendar quarters and years (ADR-102)
const now = new Date();
const qIndex = now.getUTCFullYear() * 4 + Math.floor(now.getUTCMonth() / 3);
const qText = (back) => `${Math.floor((qIndex - back) / 4)}-Q${((qIndex - back) % 4) + 1}`;
const qStart = (back) => Date.UTC(Math.floor((qIndex - back) / 4), ((qIndex - back) % 4) * 3, 1);
const thisQ = JSON.parse((await get(owner, `/api/cns-qry/insights?quarter=${qText(0)}&compare=true`)).text);
expect(ms(thisQ.from) === qStart(0) && ms(thisQ.to) > ms(thisQ.from) && ms(thisQ.previous.from) === qStart(1), 'this quarter runs from its first day to now, and is set against the quarter before', `${thisQ.from} .. ${thisQ.to}`);
expect(ms(thisQ.previous.to) - ms(thisQ.previous.from) === Math.min(ms(thisQ.to) - ms(thisQ.from), ms(thisQ.from) - ms(thisQ.previous.from)), 'against the same stretch of that quarter\'s start, never more than the quarter');
const lastQ = JSON.parse((await get(owner, `/api/cns-qry/insights?quarter=${qText(1)}&compare=true&tz=Asia/Kolkata`)).text);
expect(ms(lastQ.from) === qStart(1) - 5.5 * 3600_000 && ms(lastQ.to) === qStart(0) - 5.5 * 3600_000, 'a quarter that has ended is cut at midnight in the zone asked for', `${lastQ.from} .. ${lastQ.to}`);
expect(ms(lastQ.previous.to) === ms(lastQ.from) && ms(lastQ.previous.from) === qStart(2) - 5.5 * 3600_000, 'and is set against all of the quarter before it');
const yr = now.getUTCFullYear();
const thisY = JSON.parse((await get(owner, `/api/cns-qry/insights?year=${yr}&compare=true`)).text);
expect(ms(thisY.from) === Date.UTC(yr, 0, 1) && ms(thisY.to) > ms(thisY.from) && ms(thisY.previous.from) === Date.UTC(yr - 1, 0, 1), 'the year so far runs from 1 January to now, and is set against the year before');
expect(ms(thisY.previous.to) - ms(thisY.previous.from) === ms(thisY.to) - ms(thisY.from), 'against the same stretch of its start');
const lastY = JSON.parse((await get(owner, `/api/cns-qry/insights?year=${yr - 1}&compare=true`)).text);
expect(ms(lastY.from) === Date.UTC(yr - 1, 0, 1) && ms(lastY.to) === Date.UTC(yr, 0, 1) && ms(lastY.previous.to) === ms(lastY.from), 'a year that has ended is all of it, against all of the year before');
expect((await get(owner, `/api/cns-qry/insights?quarter=${qText(-1)}`)).status === 400, 'a quarter in the future is refused');
expect((await get(owner, `/api/cns-qry/insights?quarter=${qText(9)}`)).status === 400, 'and one more than 8 quarters back');
expect((await get(owner, '/api/cns-qry/insights?quarter=2026-Q5')).status === 400, 'and one that is not yyyy-Qn');
expect((await get(owner, `/api/cns-qry/insights?year=${yr + 1}`)).status === 400, 'a year in the future is refused');
expect((await get(owner, `/api/cns-qry/insights?year=${yr - 3}`)).status === 400, 'and one more than 2 years back');
expect((await get(owner, '/api/cns-qry/insights?year=26')).status === 400, 'and one that is not four digits');
expect((await get(owner, `/api/cns-qry/insights?month=${ym(0)}&year=${yr}`)).status === 400, 'asking for a month and a year at once is refused');
const qCsv = await get(owner, `/api/cns-qry/insights/export?quarter=${qText(1)}&view=days`);
expect(qCsv.status === 200 && qCsv.text.trim().split('\n').length - 1 >= 89, 'the CSV of a quarter has a row for each of its days', `${qCsv.text.trim().split('\n').length - 1} rows`);

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
