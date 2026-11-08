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

// Checks the DNS records that decide whether the Consent Manager's email (sign-in codes, consent requests, notices) reaches an
// inbox: SPF, DKIM, DMARC and MTA-STS for the sending domain (ADR-071, docs/runbooks/email-deliverability.md).
//
//   node scripts/email-deliverability.mjs <domain> [--dkim-selector s1,s2]
//
// Exits 1 if anything is a failure, 0 otherwise (warnings do not fail). The checks are pure functions over the TXT records, so
// they are unit tested without a network (scripts/email-deliverability.test.mjs).

import { resolveTxt } from 'node:dns/promises';
import { pathToFileURL } from 'node:url';

const join = (records) => records.map((r) => r.join(''));
const result = (status, name, detail) => ({ status, name, detail });

/** SPF: exactly one v=spf1 record, ending in -all or ~all (never +all, ?all or none), within the 10-lookup limit. */
export function checkSpf(txt) {
  const records = txt.filter((t) => /^v=spf1(\s|$)/i.test(t));
  if (records.length === 0) return result('fail', 'SPF', 'no v=spf1 record: receivers cannot tell who may send for this domain');
  if (records.length > 1) return result('fail', 'SPF', `${records.length} v=spf1 records: receivers treat that as an error and ignore both`);
  const spf = records[0];
  const terms = spf.split(/\s+/).slice(1);
  const all = terms.find((t) => /^[+\-~?]?all$/i.test(t));
  const lookups = terms.filter((t) => /^[+\-~?]?(include:|a(:|\/|$)|mx(:|\/|$)|ptr|exists:)|^redirect=/i.test(t)).length;
  if (lookups > 10) return result('fail', 'SPF', `${lookups} DNS lookups: more than 10 makes SPF fail with a permanent error`);
  if (!all && terms.some((t) => /^redirect=/i.test(t))) return result('warn', 'SPF', 'redirects to the policy of another domain: check that one too');
  if (!all) return result('warn', 'SPF', 'no "all" term: mail from other senders is not refused');
  if (/^\+?all$/i.test(all)) return result('fail', 'SPF', '"+all" lets anyone send as this domain');
  if (/^\?all$/i.test(all)) return result('warn', 'SPF', '"?all" says nothing about other senders; use ~all or -all');
  return result('pass', 'SPF', `${all.toLowerCase()} after ${lookups} lookup(s)`);
}

/** DKIM: a public key under <selector>._domainkey; a revoked key (p=) is a failure. */
export function checkDkim(selector, txt) {
  const rec = txt.find((t) => /(^|;)\s*v=DKIM1/i.test(t) || /(^|;)\s*p=/i.test(t));
  if (!rec) return result('fail', `DKIM ${selector}`, 'no key record: signed mail cannot be verified');
  const key = /(?:^|;)\s*p=([^;]*)/i.exec(rec)?.[1]?.replace(/\s+/g, '') ?? '';
  if (key === '') return result('fail', `DKIM ${selector}`, 'the key is empty: it has been revoked');
  if (/(?:^|;)\s*t=[^;]*y/i.test(rec)) return result('warn', `DKIM ${selector}`, 't=y marks the domain as testing: remove it once signing works');
  // an RSA public key in base64: about 216 characters for 1024 bits, about 392 for 2048
  if (!/k=ed25519/i.test(rec) && key.length < 300) return result('warn', `DKIM ${selector}`, 'the RSA key is shorter than 2048 bits: rotate to 2048');
  return result('pass', `DKIM ${selector}`, 'a key is published');
}

/** DMARC: a record at _dmarc; p=none only monitors; reports (rua) are how breakage is noticed. */
export function checkDmarc(txt) {
  const records = txt.filter((t) => /^v=DMARC1\s*;/i.test(t));
  if (records.length === 0) return result('fail', 'DMARC', 'no _dmarc record: receivers have no policy for mail that fails SPF and DKIM');
  if (records.length > 1) return result('fail', 'DMARC', `${records.length} DMARC records: receivers ignore them all`);
  const tags = Object.fromEntries(records[0].split(';').map((p) => p.trim()).filter(Boolean).map((p) => { const i = p.indexOf('='); return [p.slice(0, i).trim().toLowerCase(), p.slice(i + 1).trim()]; }));
  const policy = (tags.p ?? '').toLowerCase();
  if (!['none', 'quarantine', 'reject'].includes(policy)) return result('fail', 'DMARC', `p=${tags.p ?? '(missing)'} is not none, quarantine or reject`);
  const pct = tags.pct === undefined ? 100 : Number(tags.pct);
  if (policy === 'none') return result('warn', 'DMARC', 'p=none only monitors: spoofed mail is still delivered; move to quarantine, then reject');
  if (!tags.rua) return result('warn', 'DMARC', `p=${policy} but no rua= address: failures will not be reported to anyone`);
  if (pct < 100) return result('warn', 'DMARC', `p=${policy} applies to only ${pct}% of failing mail`);
  return result('pass', 'DMARC', `p=${policy}, reports to ${tags.rua}`);
}

/** MTA-STS: _mta-sts TXT, which makes sending servers require TLS to this domain's inbound mail. Advisory. */
export function checkMtaSts(txt) {
  const rec = txt.find((t) => /^v=STSv1\s*;/i.test(t));
  return rec ? result('pass', 'MTA-STS', 'published') : result('warn', 'MTA-STS', 'not published: mail to this domain may fall back to unencrypted delivery (advisory)');
}

const lookup = async (name) => {
  try { return join(await resolveTxt(name)); } catch (e) { if (['ENOTFOUND', 'ENODATA'].includes(e.code)) return []; throw e; }
};

export async function check(domain, selectors = [], resolver = lookup) {
  const out = [checkSpf(await resolver(domain)), checkDmarc(await resolver(`_dmarc.${domain}`))];
  if (selectors.length === 0) out.push(result('warn', 'DKIM', 'no --dkim-selector given: not checked (ask the mail provider for its selector)'));
  for (const s of selectors) out.push(checkDkim(s, await resolver(`${s}._domainkey.${domain}`)));
  out.push(checkMtaSts(await resolver(`_mta-sts.${domain}`)));
  return out;
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  const args = process.argv.slice(2);
  const domain = args.find((a) => !a.startsWith('--') && args[args.indexOf(a) - 1] !== '--dkim-selector');
  const at = args.indexOf('--dkim-selector');
  const selectors = at >= 0 ? (args[at + 1] ?? '').split(',').filter(Boolean) : [];
  if (!domain) { console.error('usage: email-deliverability.mjs <domain> [--dkim-selector s1,s2]'); process.exit(2); }
  const rows = await check(domain, selectors);
  for (const r of rows) console.log(`${r.status.toUpperCase().padEnd(4)}  ${r.name.padEnd(14)} ${r.detail}`);
  process.exitCode = rows.some((r) => r.status === 'fail') ? 1 : 0;
}
