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

import test from 'node:test';
import assert from 'node:assert/strict';
import { check, checkDkim, checkDmarc, checkMtaSts, checkSpf } from './email-deliverability.mjs';

const status = (r) => r.status;

test('SPF: a record ending in -all or ~all passes', () => {
  assert.equal(status(checkSpf(['v=spf1 include:_spf.example.net -all'])), 'pass');
  assert.equal(status(checkSpf(['v=spf1 ip4:192.0.2.1 ~all'])), 'pass');
});
test('SPF: none, two, +all and more than ten lookups fail', () => {
  assert.equal(status(checkSpf(['google-site-verification=abc'])), 'fail');
  assert.equal(status(checkSpf(['v=spf1 -all', 'v=spf1 ~all'])), 'fail');
  assert.equal(status(checkSpf(['v=spf1 +all'])), 'fail');
  const many = 'v=spf1 ' + Array.from({ length: 11 }, (_, i) => `include:s${i}.example.net`).join(' ') + ' -all';
  assert.equal(status(checkSpf([many])), 'fail');
});
test('SPF: ?all and a missing all only warn', () => {
  assert.equal(status(checkSpf(['v=spf1 include:a.example.net ?all'])), 'warn');
  assert.equal(status(checkSpf(['v=spf1 include:a.example.net'])), 'warn');
});

const rsa2048 = 'p=' + 'A'.repeat(392);
test('DKIM: a 2048-bit key passes; empty, missing, testing and short keys do not', () => {
  assert.equal(status(checkDkim('s1', [`v=DKIM1; k=rsa; ${rsa2048}`])), 'pass');
  assert.equal(status(checkDkim('s1', ['v=DKIM1; k=rsa; p='])), 'fail');
  assert.equal(status(checkDkim('s1', [])), 'fail');
  assert.equal(status(checkDkim('s1', [`v=DKIM1; t=y; ${rsa2048}`])), 'warn');
  assert.equal(status(checkDkim('s1', ['v=DKIM1; k=rsa; p=' + 'A'.repeat(216)])), 'warn');
  assert.equal(status(checkDkim('s1', ['v=DKIM1; k=ed25519; p=' + 'A'.repeat(44)])), 'pass');
});

test('DMARC: reject or quarantine with reports passes; none warns; missing or malformed fails', () => {
  assert.equal(status(checkDmarc(['v=DMARC1; p=reject; rua=mailto:d@example.com'])), 'pass');
  assert.equal(status(checkDmarc(['v=DMARC1; p=quarantine; rua=mailto:d@example.com'])), 'pass');
  assert.equal(status(checkDmarc(['v=DMARC1; p=none; rua=mailto:d@example.com'])), 'warn');
  assert.equal(status(checkDmarc(['v=DMARC1; p=reject'])), 'warn');
  assert.equal(status(checkDmarc(['v=DMARC1; p=reject; pct=25; rua=mailto:d@example.com'])), 'warn');
  assert.equal(status(checkDmarc([])), 'fail');
  assert.equal(status(checkDmarc(['v=DMARC1; p=banana'])), 'fail');
});

test('MTA-STS is advisory', () => {
  assert.equal(status(checkMtaSts(['v=STSv1; id=1'])), 'pass');
  assert.equal(status(checkMtaSts([])), 'warn');
});

test('check reads the right names', async () => {
  const asked = [];
  const dns = { 'example.com': ['v=spf1 -all'], '_dmarc.example.com': ['v=DMARC1; p=reject; rua=mailto:d@example.com'], 's1._domainkey.example.com': [`v=DKIM1; ${rsa2048}`] };
  const rows = await check('example.com', ['s1'], async (n) => { asked.push(n); return dns[n] ?? []; });
  assert.deepEqual(asked, ['example.com', '_dmarc.example.com', 's1._domainkey.example.com', '_mta-sts.example.com']);
  assert.deepEqual(rows.map(status), ['pass', 'pass', 'pass', 'warn']);
});
