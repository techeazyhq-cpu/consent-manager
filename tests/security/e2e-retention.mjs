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

// The retention purge of a closed fiduciary, end to end on the local stack (ADR-064). Takes a CLOSED "Closing Test …"
// fiduciary that e2e-closing.mjs left (run that first), and checks that:
//   - its consent records, events and receipts can not be deleted by anything but the purge (the append-only trigger holds)
//   - a legal hold stops the purge, and nothing is deleted while it is held
//   - once released, its records, read-model rows, ledger payloads and acceptances are deleted
//   - the ledger's entries and hash chain stay, and still verify
//   - another fiduciary's records are untouched
// The purge normally waits 7 years, so this needs cm-df-reg-svc started with a retention of 0 days and a short pass, e.g.
//   CM_DFREG_RETENTION_CONSENT-DAYS=0  CM_DFREG_RETENTION_FIXED-DELAY-MS=15000  CM_DFREG_RETENTION_INITIAL-DELAY-MS=5000
// and it reads the local databases with docker exec.
//
// With a retention of 0 the pass would purge a fresh closed fiduciary before the test could hold it, so first run
//   node tests/security/e2e-retention.mjs prepare      (puts a legal hold on the newest closed "Closing Test …")
// then restart cm-df-reg-svc with the short retention, and run the test itself:
//   node tests/security/e2e-retention.mjs

import { execFileSync } from 'node:child_process';
import { GW, KC } from './lib/cm1.mjs';

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
const psql = (sql) => {
  try {
    return execFileSync('docker', ['exec', 'common-postgres-1', 'psql', '-U', 'cm', '-d', 'consent_manager', '-At', '-c', sql], { stdio: 'pipe' }).toString().trim();
  } catch (e) {
    return `ERROR ${e.stderr?.toString() ?? e.message}`;
  }
};
const count = (sql) => Number(psql(sql));

const staff = (await (await fetch(`${KC}/realms/cm-staff/protocol/openid-connect/token`, { method: 'POST',
  body: new URLSearchParams({ grant_type: 'password', client_id: 'cm-cm-admin', username: 'admin@cm.local', password: 'admin' }) })).json()).access_token;
const dfs = (await api(staff, 'GET', '/api/df-reg/dfs')).json;
const prepare = process.argv[2] === 'prepare';
const candidates = dfs.filter((d) => d.sts === 'CLOSED' && d.name.startsWith('Closing Test') && !d.purgedAt);
const df = (prepare ? candidates : candidates.filter((d) => d.legalHoldReason === 'E2E test hold'))[0];
if (!df) {
  console.log(prepare ? 'No closed, unpurged "Closing Test" fiduciary: run tests/security/e2e-closing.mjs first.'
    : 'No held "Closing Test" fiduciary: run this with "prepare" first.');
  process.exit(2);
}
if (prepare) {
  const held = await api(staff, 'PUT', `/api/df-reg/dfs/${df.id}/legal-hold`, { reason: 'E2E test hold' });
  console.log(`${held.status === 200 ? 'Held' : 'Could not hold'} ${df.name}. Now restart cm-df-reg-svc with the short retention and run the test.`);
  process.exit(held.status === 200 ? 0 : 1);
}
const other = dfs.find((d) => d.name === 'Security Test B Ltd' || d.name === 'Batch Test Co');
const id = df.id;
const sizes = () => ({
  reqs: count(`select count(*) from cns_cmd.cns_req where df_id = '${id}'`),
  events: count(`select count(*) from cns_cmd.cns_rec_evt e join cns_cmd.cns_rec c on c.id = e.rec_id where c.df_id = '${id}'`),
  receipts: count(`select count(*) from cns_cmd.cns_rcpt r join cns_cmd.cns_rec c on c.id = r.rec_id where c.df_id = '${id}'`),
  readModel: count(`select count(*) from cns_qry.cns_art_rd_mdl where df_id = '${id}'`),
  payloads: count(`select count(*) from aud.entry_payload p join aud.entry e on e.seq = p.seq where e.df_id = '${id}'`),
  entries: count(`select count(*) from aud.entry where df_id = '${id}'`),
  acceptances: count(`select count(*) from df_reg.df_terms_acceptance where df_id = '${id}'`),
  purposes: count(`select count(*) from cns_cmd.prps_def where df_id = '${id}'`),
});
const otherBefore = other ? count(`select count(*) from cns_cmd.cns_req where df_id = '${other.id}'`) : 0;

const before = sizes();
expect(before.reqs > 0 && before.events > 0 && before.payloads > 0 && before.entries > 0, 'the closed fiduciary still has its records', JSON.stringify(before));

const refused = psql(`delete from cns_cmd.cns_rec_evt where rec_id in (select id from cns_cmd.cns_rec where df_id = '${id}')`);
expect(refused.includes('append-only'), 'nothing but the purge can delete a consent event', refused.slice(0, 80));
expect(count(`select count(*) from cns_cmd.cns_rec_evt e join cns_cmd.cns_rec c on c.id = e.rec_id where c.df_id = '${id}'`) === before.events, 'and none was deleted');

expect(df.legalHoldReason === 'E2E test hold', 'staff placed a legal hold (prepare)');
const noReason = await api(staff, 'PUT', `/api/df-reg/dfs/${id}/legal-hold`, {});
expect(noReason.status === 400, 'a hold needs a reason', `${noReason.status}`);
console.log('(waiting 40 s: the purge pass must leave a held fiduciary alone)');
await sleep(40_000);
const whileHeld = sizes();
expect(JSON.stringify(whileHeld) === JSON.stringify(before), 'nothing is deleted while it is held');
const stillListed = (await api(staff, 'GET', '/api/df-reg/dfs')).json.find((d) => d.id === id);
expect(!stillListed.purgedAt && stillListed.legalHoldReason === 'E2E test hold', 'and the list shows the hold');

const released = await api(staff, 'DELETE', `/api/df-reg/dfs/${id}/legal-hold`);
expect(released.status === 200 && !released.json.legalHoldReason, 'staff release the hold', `${released.status}`);
const twice = await api(staff, 'DELETE', `/api/df-reg/dfs/${id}/legal-hold`);
expect(twice.status === 409 && twice.json.code === 'df-no-legal-hold', 'releasing twice is refused', `${twice.status} ${twice.json?.code}`);

let purged = null;
for (let i = 0; i < 30 && !purged; i++) {
  await sleep(4000);
  const d = (await api(staff, 'GET', '/api/df-reg/dfs')).json.find((x) => x.id === id);
  purged = d?.purgedAt ? d : null;
}
expect(!!purged, 'the fiduciary is marked purged', purged?.purgedAt);
const after = sizes();
expect(after.reqs === 0 && after.events === 0 && after.receipts === 0, 'its consent requests, events and receipts are deleted', JSON.stringify({ reqs: after.reqs, events: after.events, receipts: after.receipts }));
expect(after.readModel === 0, 'its read-model rows are deleted');
// the hold being released and the purge itself are audited after the cut-off, so those payloads are rightly still there
const added = after.entries - before.entries;
expect(after.payloads <= added, 'its ledger payloads from before are deleted', `${after.payloads} left, ${added} entries added since`);
expect(after.entries >= before.entries, 'but the ledger entries stay', `${after.entries} entries`);
expect(after.acceptances === 0 && after.purposes === 0, 'its acceptances and purposes are deleted');
let verify;
for (let i = 0; i < 4; i++) {
  verify = await api(staff, 'GET', '/api/aud/chain/verify'); // a full verification takes several seconds
  if (verify.status === 200) break;
  await sleep(5000);
}
expect(verify.status === 200 && verify.json.chain.intact && verify.json.checkpoints.intact, 'the ledger chain and checkpoints still verify',
  `${verify.json?.chain?.entriesChecked} entries checked, ${verify.json?.chain?.payloadsErased} payloads erased`);
expect(!other || count(`select count(*) from cns_cmd.cns_req where df_id = '${other.id}'`) === otherBefore, 'another fiduciary\'s records are untouched');

console.log(failed ? '\nSome checks failed.' : '\nAll checks passed.');
process.exitCode = failed ? 1 : 0;
