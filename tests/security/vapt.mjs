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

// Scripted penetration checks of the Data Fiduciary API through the gateway (local stack only): authentication,
// request signing, replay, cross-fiduciary access, privilege, input handling, and what responses give away. Each check
// states what a secure answer is; the run prints a table and exits non-zero if any check fails.
//
//   KEYS_FILE=/tmp/cm-keys.json node tests/security/setup-keys.mjs
//   KEYS_FILE=/tmp/cm-keys.json node tests/security/vapt.mjs [--json results.json]

import { randomBytes, randomUUID } from 'node:crypto';
import { writeFileSync } from 'node:fs';
import { GW, call, dfSignIn, loadKeys, send, sign } from './lib/cm1.mjs';

const K = loadKeys(process.env.KEYS_FILE ?? (() => { throw new Error('Set KEYS_FILE'); })());
// console tokens last minutes: sign A's owner in now rather than use the one saved at setup
K.tokenA = await dfSignIn('batch-owner@example.test');
const results = [];
const STACK = /(\bat [a-z]+\.[\w.$]+\(|Exception|\.java:\d+|Caused by|stackTrace|"trace")/;

async function check(id, area, name, expected, fn) {
  let pass = false;
  let actual;
  try {
    [pass, actual] = await fn();
  } catch (e) {
    actual = `error: ${e.message}`;
  }
  results.push({ id, area, name, expected, actual, pass });
  console.log(`${pass ? 'PASS' : 'FAIL'}  ${id}  ${name}  ->  ${actual}`);
}
const is = (r, status, title) => [r.status === status && (!title || r.json?.title === title),
  `${r.status}${r.json?.title ? ` ${r.json.title}` : ''}`];
const refused = (r) => [r.status >= 400 && r.status < 500 && !STACK.test(r.text), `${r.status}${STACK.test(r.text) ? ' (stack trace)' : ''}`];

const LIST = '/api/cns-qry/cns-arts';
const RAISE = '/api/cns-cmd/cns-reqs';
const request = (who) => ({ extDpRefTyp: 'EMAIL', extDpRefVal: `vapt-${who}-${randomBytes(3).toString('hex')}@example.test`,
  prpsCode: 'ORDER_UPDATES', prpsVer: (who === 'b' ? K.prpsVerB : K.prpsVerA) ?? 1 });
const PURPOSES = '/api/cns-cmd/prps-defs';
/** A purpose definition; `over` replaces parts of its content, `notice` of its one notice. */
const purpose = (code, over = {}, notice = {}) => ({ code, content: { name: 'Security test', dCats: [{ code: 'EMAIL',
  sensitivity: 'ORDINARY', necessary: false }], recipients: [], transfers: [], retentionDays: 30, requestedValidityDays: 30,
  maxValidityDays: 30, flags: [], notices: [{ lang: 'en', title: 'Security test', text: 'Raised by tests/security/vapt.mjs.',
  ...notice }], ...over } });

// ---------------------------------------------------------------- fixtures: one request each, A's and B's
const mineA = await call(K.a, 'POST', RAISE, { body: request('a') });
const mineB = await call(K.b, 'POST', RAISE, { body: request('b') });
if (mineA.status !== 201 || mineB.status !== 201) {
  throw new Error(`Could not raise the fixture requests: A ${mineA.status} ${mineA.text}, B ${mineB.status} ${mineB.text}`);
}
const reqA = mineA.json.id;
const reqB = mineB.json.id;

// ---------------------------------------------------------------- authentication
await check('AUTH-01', 'Authentication', 'No credentials', '401', async () => is(await send('GET', new URL(LIST, GW), {}), 401));
await check('AUTH-02', 'Authentication', 'Unknown key, correctly signed', '401 api-key-invalid', async () =>
  is(await call({ apiKey: `cmk_${randomBytes(24).toString('base64url')}`, signingSecret: 'x' }, 'GET', LIST), 401, 'api-key-invalid'));
await check('AUTH-03', 'Authentication', 'Revoked key', '401 api-key-invalid', async () =>
  is(await call(K.aRevoked, 'GET', LIST), 401, 'api-key-invalid'));
await check('AUTH-04', 'Authentication', 'Malformed key ("x")', '401, no stack trace', async () =>
  refused(await call({ apiKey: 'x', signingSecret: 'x' }, 'GET', LIST)));
await check('AUTH-05', 'Authentication', '8 KB key', '4xx, no stack trace', async () =>
  refused(await call({ apiKey: `cmk_${'A'.repeat(8000)}`, signingSecret: 'x' }, 'GET', LIST)));
await check('AUTH-06', 'Authentication', 'Key and a bearer token together', '400 ambiguous-credentials', async () =>
  is(await call(K.a, 'GET', LIST, { headers: { Authorization: `Bearer ${K.tokenA}` } }), 400, 'ambiguous-credentials'));
await check('AUTH-07', 'Authentication', 'Key in the query string instead of the header', '401', async () =>
  is(await send('GET', new URL(`${LIST}?api_key=${K.a.apiKey}`, GW), {}), 401));
await check('AUTH-08', 'Authentication', 'Forged bearer token (alg none)', '401', async () => {
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const forged = `${b64({ alg: 'none', typ: 'JWT' })}.${b64({ sub: 'x', azp: `df-api-${K.dfA}`, df_id: K.dfA,
    exp: Math.floor(Date.now() / 1000) + 600 })}.`;
  return is(await send('GET', new URL(LIST, GW), { Authorization: `Bearer ${forged}` }), 401);
});

// ---------------------------------------------------------------- signatures
await check('SIG-01', 'Signing', 'Unsigned write with a signing key', '401 signature-required', async () =>
  is(await call(K.a, 'POST', RAISE, { body: request('a'), unsigned: true }), 401, 'signature-required'));
await check('SIG-02', 'Signing', 'Signed with another secret', '401 signature-invalid', async () =>
  is(await call(K.a, 'GET', LIST, { over: { secret: K.b.signingSecret } }), 401, 'signature-invalid'));
await check('SIG-03', 'Signing', 'Body changed after signing', '401 signature-invalid', async () => {
  const body = JSON.stringify(request('a'));
  const h = sign(K.a.signingSecret, 'POST', RAISE, '', body);
  return is(await call(K.a, 'POST', RAISE, { body: body.replace('ORDER_UPDATES', 'MARKETING'), signHeaders: h }), 401, 'signature-invalid');
});
await check('SIG-04', 'Signing', 'Path changed after signing', '401 signature-invalid', async () =>
  is(await call(K.a, 'GET', `${LIST}/${reqB}`, { over: { path: LIST } }), 401, 'signature-invalid'));
await check('SIG-05', 'Signing', 'Query changed after signing', '401 signature-invalid', async () =>
  is(await call(K.a, 'GET', `${LIST}?limit=500`, { over: { query: 'limit=5' } }), 401, 'signature-invalid'));
await check('SIG-06', 'Signing', 'Method changed after signing (signed GET, sent POST)', '401 signature-invalid', async () =>
  is(await call(K.a, 'POST', RAISE, { body: request('a'), over: { method: 'GET' } }), 401, 'signature-invalid'));
await check('SIG-07', 'Signing', 'Signature truncated to 32 hex digits', '401 signature-invalid', async () => {
  const h = sign(K.a.signingSecret, 'GET', LIST, '', '');
  h['X-Signature'] = h['X-Signature'].slice(0, 32);
  return is(await call(K.a, 'GET', LIST, { signHeaders: h }), 401, 'signature-invalid');
});
await check('SIG-08', 'Signing', 'Timestamp 301 s old', '401 signature-expired', async () =>
  is(await call(K.a, 'GET', LIST, { over: { ts: `${Math.floor(Date.now() / 1000) - 301}` } }), 401, 'signature-expired'));
await check('SIG-09', 'Signing', 'Timestamp 301 s ahead', '401 signature-expired', async () =>
  is(await call(K.a, 'GET', LIST, { over: { ts: `${Math.floor(Date.now() / 1000) + 301}` } }), 401, 'signature-expired'));
await check('SIG-10', 'Signing', 'Timestamp 290 s old (inside the window)', '200', async () =>
  is(await call(K.a, 'GET', LIST, { over: { ts: `${Math.floor(Date.now() / 1000) - 290}` } }), 200));
await check('SIG-11', 'Signing', 'Timestamp not a number / 30 digits', '401, no stack trace', async () => {
  const a = await call(K.a, 'GET', LIST, { over: { ts: 'yesterday' } });
  const b = await call(K.a, 'GET', LIST, { over: { ts: '9'.repeat(30) } });
  return [a.status === 401 && b.status === 401 && !STACK.test(a.text + b.text), `${a.status}, ${b.status}`];
});
await check('SIG-12', 'Signing', 'Nonce 8 chars / 65 chars / with "../"', '401 each', async () => {
  const s = await Promise.all(['abcdefgh', 'a'.repeat(65), '../../etc/passwd-1234'].map((nonce) =>
    call(K.a, 'GET', LIST, { over: { nonce } })));
  return [s.every((r) => r.status === 401), s.map((r) => r.status).join(', ')];
});
await check('SIG-13', 'Signing', 'Signature headers present, key header absent', '401', async () => {
  const h = sign(K.a.signingSecret, 'GET', LIST, '', '');
  return is(await send('GET', new URL(LIST, GW), h), 401);
});
// As notification-service's NS1: a signing key needs signatures on what changes state, so a leaked key alone can read
// but not write. Recorded in the report as a residual risk.
await check('SIG-14', 'Signing', 'Unsigned read with a signing key', '200 (signatures are required on writes, as NS1)', async () =>
  is(await call(K.a, 'GET', LIST, { unsigned: true }), 200));
await check('SIG-15', 'Signing', 'Unsigned read with a key that does not require signing', '200 (by the key\'s setting)', async () =>
  is(await call(K.aUnsigned, 'GET', LIST, { unsigned: true }), 200));

// ---------------------------------------------------------------- replay
await check('RPL-01', 'Replay', 'The same signed write sent twice', 'first 201, then 409 request-replayed', async () => {
  const body = JSON.stringify(request('a'));
  const h = sign(K.a.signingSecret, 'POST', RAISE, '', body);
  const first = await call(K.a, 'POST', RAISE, { body, signHeaders: h });
  const second = await call(K.a, 'POST', RAISE, { body, signHeaders: h });
  return [first.status === 201 && second.status === 409 && second.json?.title === 'request-replayed',
    `${first.status}, ${second.status} ${second.json?.title ?? ''}`];
});
await check('RPL-02', 'Replay', 'The same signed write sent 25 times at once', 'exactly one 201, the rest 409', async () => {
  const body = JSON.stringify(request('a'));
  const h = sign(K.a.signingSecret, 'POST', RAISE, '', body);
  const all = await Promise.all(Array.from({ length: 25 }, () => call(K.a, 'POST', RAISE, { body, signHeaders: h })));
  const ok = all.filter((r) => r.status === 201).length;
  const replayed = all.filter((r) => r.status === 409).length;
  return [ok === 1 && replayed === 24, `${ok} accepted, ${replayed} refused as replays, others ${all.length - ok - replayed}`];
});
await check('RPL-03', 'Replay', 'A used nonce, re-signed with a fresh timestamp', '409 request-replayed', async () => {
  const nonce = randomBytes(16).toString('hex');
  await call(K.a, 'GET', LIST, { over: { nonce } });
  return is(await call(K.a, 'GET', LIST, { over: { nonce, ts: `${Math.floor(Date.now() / 1000) + 1}` } }), 409, 'request-replayed');
});
await check('RPL-04', 'Replay', 'A signed read sent twice', 'second 409', async () => {
  const h = sign(K.a.signingSecret, 'GET', LIST, '', '');
  await call(K.a, 'GET', LIST, { signHeaders: h });
  return is(await call(K.a, 'GET', LIST, { signHeaders: h }), 409, 'request-replayed');
});
await check('RPL-05', 'Replay', 'A captured request replayed with another key', '401 (the signature names one secret)', async () => {
  const body = JSON.stringify(request('a'));
  const h = sign(K.a.signingSecret, 'POST', RAISE, '', body);
  await call(K.a, 'POST', RAISE, { body, signHeaders: h });
  return is(await call(K.b, 'POST', RAISE, { body, signHeaders: h }), 401, 'signature-invalid');
});

// ---------------------------------------------------------------- one fiduciary against another's data
await check('IDOR-01', 'Access control', "A's key reads B's request", '404', async () =>
  is(await call(K.a, 'GET', `${LIST}/${reqB}`), 404));
await check('IDOR-02', 'Access control', "A's key reads B's request history", '404 or empty', async () => {
  const r = await call(K.a, 'GET', `${LIST}/${reqB}/history`);
  return [r.status === 404 || (r.status === 200 && Array.isArray(r.json) && r.json.length === 0), `${r.status} ${r.status === 200 ? `${r.json?.length} entries` : ''}`];
});
await check('IDOR-03', 'Access control', "A's list holds only A's requests", 'every row has A\'s dfId', async () => {
  const r = await call(K.a, 'GET', LIST);
  const foreign = (r.json ?? []).filter((x) => x.dfId !== K.dfA).length;
  return [r.status === 200 && foreign === 0 && r.json.some((x) => x.cnsReqId === reqA), `${r.status}, ${r.json?.length} rows, ${foreign} not A's`];
});
await check('IDOR-04', 'Access control', "B's key asks a token for A's consent", '404', async () =>
  is(await call(K.b, 'POST', `/api/cns-cmd/cns-arts/${K.grantedA.cnsArtId}/token`), 404));
await check('IDOR-05', 'Access control', "A's console user reads B's request", '404', async () =>
  is(await send('GET', new URL(`${LIST}/${reqB}`, GW), { Authorization: `Bearer ${K.tokenA}` }), 404));
await check('IDOR-06', 'Access control', "Raising a request with B's dfId in the body", "created for A, or refused", async () => {
  const r = await call(K.a, 'POST', RAISE, { body: { ...request('a'), dfId: K.dfB } });
  return [(r.status === 201 && r.json?.dfId === K.dfA) || (r.status >= 400 && r.status < 500), `${r.status} dfId=${r.json?.dfId === K.dfA ? 'A' : r.json?.dfId === K.dfB ? 'B' : r.json?.dfId}`];
});
await check('IDOR-07', 'Access control', "A's key reads B's batch (random and B-shaped ids)", '404', async () =>
  is(await call(K.a, 'GET', `/api/cns-cmd/cns-req-batches/${randomUUID()}`), 404));
await check('IDOR-08', 'Access control', "A's key reads the subscription", "A's only", async () => {
  const r = await call(K.a, 'GET', '/api/bil/me/subscription');
  return [r.status === 200 && (r.json?.dfId === undefined || r.json.dfId === K.dfA), `${r.status} dfId=${r.json?.dfId === K.dfA ? 'A' : r.json?.dfId}`];
});
await check('IDOR-09', 'Access control', "A's key reads activity", "A's entries only", async () => {
  const r = await call(K.a, 'GET', '/api/aud/activity?limit=50');
  const rows = Array.isArray(r.json) ? r.json : r.json?.items ?? [];
  const foreign = rows.filter((x) => x.dfId && x.dfId !== K.dfA).length;
  return [r.status === 200 && foreign === 0, `${r.status}, ${rows.length} rows, ${foreign} not A's`];
});

await check('IDOR-10', 'Access control', "B verifies A's consent with A's own token", '403 (a token works only for its fiduciary)', async () => {
  const t = await call(K.a, 'POST', `/api/cns-cmd/cns-arts/${K.grantedA.cnsArtId}/token`);
  const verify = (k) => call(k, 'GET', `/api/cns-qry/cns-arts/${K.grantedA.cnsArtId}/verify?purpose=ORDER_UPDATES`,
    { headers: { 'X-Cns-Tkn': t.json?.cnsTkn ?? '' } });
  const mine = await verify(K.a);
  const theirs = await verify(K.b);
  return [t.status === 200 && mine.status === 200 && theirs.status === 403, `token ${t.status}; A ${mine.status}; B ${theirs.status}`];
});
await check('IDOR-11', 'Access control', 'A consent token for another purpose', '403', async () => {
  const t = await call(K.a, 'POST', `/api/cns-cmd/cns-arts/${K.grantedA.cnsArtId}/token`);
  return is(await call(K.a, 'GET', `/api/cns-qry/cns-arts/${K.grantedA.cnsArtId}/verify?purpose=MARKETING`,
    { headers: { 'X-Cns-Tkn': t.json?.cnsTkn ?? '' } }), 403);
});
await check('IDOR-12', 'Access control', "A consent token tampered with (payload changed, signature kept)", '403', async () => {
  const t = (await call(K.a, 'POST', `/api/cns-cmd/cns-arts/${K.grantedA.cnsArtId}/token`)).json?.cnsTkn ?? '..';
  const [h, p, sig] = t.split('.');
  const claims = JSON.parse(Buffer.from(p, 'base64url').toString());
  const forged = `${h}.${Buffer.from(JSON.stringify({ ...claims, purpose_code: 'MARKETING' })).toString('base64url')}.${sig}`;
  return is(await call(K.a, 'GET', `/api/cns-qry/cns-arts/${K.grantedA.cnsArtId}/verify?purpose=MARKETING`,
    { headers: { 'X-Cns-Tkn': forged } }), 403);
});

// ---------------------------------------------------------------- privilege: an API key is not a person
for (const [id, method, path, body, why] of [
  ['PRIV-01', 'GET', '/api/df-reg/me/api-keys', undefined, 'list keys'],
  ['PRIV-02', 'POST', '/api/df-reg/me/api-keys', { name: 'escalate', signingRequired: false }, 'issue a key'],
  ['PRIV-03', 'POST', '/api/df-reg/me/members', { email: 'x@example.test', name: 'x', role: 'OWNER' }, 'invite an owner'],
  ['PRIV-04', 'GET', '/api/df-reg/dfs', undefined, 'list every fiduciary (staff)'],
  ['PRIV-05', 'GET', '/api/stf/staff', undefined, 'list staff'],
  ['PRIV-06', 'GET', '/api/aud/chain/verify', undefined, 'verify the ledger (staff)'],
  ['PRIV-07', 'PUT', `/api/bil/dfs/${K.dfA}/sub-pln`, { subPlnName: 'ENTERPRISE' }, 'change its own plan (staff)'],
  ['PRIV-08', 'POST', `/api/df-reg/dfs/${K.dfB}/activate`, undefined, 'activate a fiduciary (staff)'],
]) {
  await check(id, 'Privilege', `A key tries to ${why}`, '403 (or 401/404)', async () => {
    const r = await call(K.a, method, path, { body });
    return [[401, 403, 404, 405].includes(r.status), `${r.status}`];
  });
}
await check('PRIV-09', 'Privilege', 'The internal key check through the gateway', 'not reachable', async () => {
  const a = await send('POST', new URL('/internal/api-keys/verify', GW), { 'Content-Type': 'application/json' }, '{}');
  const b = await send('POST', new URL('/api/df-reg/%2e%2e/%2e%2e/internal/api-keys/verify', GW), { 'Content-Type': 'application/json' }, '{}');
  const c = await send('POST', new URL('/api/df-reg/..;/..;/internal/api-keys/verify', GW), { 'Content-Type': 'application/json' }, '{}');
  return [![a, b, c].some((r) => r.status === 200 || r.json?.accessToken), `${a.status}, ${b.status}, ${c.status}`];
});

// ---------------------------------------------------------------- input handling
await check('INP-01', 'Input', 'Malformed JSON', '400, no stack trace', async () => refused(await call(K.a, 'POST', RAISE, { body: '{"extDpRefTyp":' })));
await check('INP-02', 'Input', 'A 13 MB signed body', '413 payload-too-large', async () =>
  is(await call(K.a, 'POST', RAISE, { body: `{"x":"${'a'.repeat(13 * 1024 * 1024)}"}` }), 413, 'payload-too-large'));
await check('INP-03', 'Input', 'SQL in a field and in the purpose query', '4xx or stored as text; never 500', async () => {
  const a = await call(K.a, 'POST', RAISE, { body: { ...request('a'), extDpRefVal: "x'); DROP TABLE cns_cmd.cns_req;--@example.test" } });
  const b = await call(K.a, 'GET', `/api/cns-qry/cns-arts/${randomUUID()}/verify?purpose=${encodeURIComponent("' OR '1'='1")}`);
  return [a.status < 500 && b.status < 500 && !STACK.test(a.text + b.text), `${a.status}, ${b.status}`];
});
await check('INP-04', 'Input', "Script in a purpose's notice title", 'refused or returned as JSON (never HTML)', async () => {
  const code = `VAPT_SCRIPT_${randomBytes(3).toString('hex').toUpperCase()}`;
  const r = await call(K.a, 'POST', PURPOSES, { body: purpose(code, {}, { title: '<script>alert(1)</script>' }) });
  return [r.status < 500 && !String(r.headers.get('content-type')).includes('html'), `${r.status} ${r.headers.get('content-type')}`];
});
await check('INP-05', 'Input', 'Not a UUID in the path', '400 or 404, no stack trace', async () =>
  refused(await call(K.a, 'GET', `${LIST}/not-a-uuid'%20OR%201=1`)));
await check('INP-06', 'Input', 'XML instead of JSON', '400 or 415', async () =>
  refused(await call(K.a, 'POST', RAISE, { body: '<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><x>&e;</x>', headers: { 'Content-Type': 'application/xml' } })));
await check('INP-07', 'Input', 'A 10 000-entry data category list', '400', async () =>
  refused(await call(K.a, 'POST', RAISE, { body: { ...request('a'), dCatCodes: Array.from({ length: 10000 }, (_, i) => `C${i}`) } })));
await check('INP-08', 'Input', 'A 65-character purpose code; a 300-character notice title', '400 each (not a database error)', async () => {
  const a = await call(K.a, 'POST', PURPOSES, { body: purpose('P'.repeat(65)) });
  const b = await call(K.a, 'POST', PURPOSES, { body: purpose('VAPT_LONG_TITLE', {}, { title: 'T'.repeat(300) }) });
  return [a.status === 400 && b.status === 400, `${a.status}, ${b.status}`];
});
await check('INP-09', 'Input', "Data the purpose doesn't cover; a purpose with none", '400 each', async () => {
  const a = await call(K.a, 'POST', RAISE, { body: { ...request('a'), dCatCodes: ['EMAIL', 'LOCATION'] } });
  const b = await call(K.a, 'POST', PURPOSES, { body: purpose('VAPT_NO_DATA', { dCats: [] }) });
  return [a.status === 400 && b.status === 400, `${a.status}, ${b.status}`];
});
// found by the ZAP scan: a NUL reached Postgres (500), and a notice of only a control character was stored empty, after
// which no listing of that fiduciary's batches worked
await check('INP-10', 'Input', "A NUL in a purpose's notice, its title, a data category", '400 each', async () => {
  const rs = await Promise.all([
    purpose('VAPT_NUL_TEXT', {}, { text: 'a\u0000b' }),
    purpose('VAPT_NUL_TITLE', {}, { title: 'a\u0000b' }),
    purpose('VAPT_NUL_DCAT', { dCats: [{ code: 'EM\u0000AIL', sensitivity: 'ORDINARY', necessary: false }] }),
  ].map((body) => call(K.a, 'POST', PURPOSES, { body })));
  return [rs.every((r) => r.status === 400), rs.map((r) => r.status).join(', ')];
});
await check('INP-11', 'Input', 'A notice of one control character; a batch for a version that was never published; then the batch list',
  '400; 404; the list 200', async () => {
    const notice = await call(K.a, 'POST', PURPOSES, { body: purpose('VAPT_CTRL', {}, { text: '\u0001' }) });
    const batch = await call(K.a, 'POST', '/api/cns-cmd/cns-req-batches?dryRun=false', { body: { prpsCode: 'ORDER_UPDATES',
      prpsVer: 999, recipients: [{ email: 'vapt-batch@example.test' }] } });
    const list = await call(K.a, 'GET', '/api/cns-cmd/cns-req-batches');
    return [notice.status === 400 && batch.status === 404 && list.status === 200,
      `${notice.status}; ${batch.status}; list ${list.status}`];
  });

// ---------------------------------------------------------------- purposes (ADR-031)
const ownCode = `VAPT_A_${randomBytes(3).toString('hex').toUpperCase()}`;
await call(K.a, 'POST', PURPOSES, { body: purpose(ownCode) });
await call(K.a, 'POST', `${PURPOSES}/${ownCode}/publish`);
await check('PRP-01', 'Access control', "B reads or asks for A's purpose", '404 each', async () => {
  const read = await call(K.b, 'GET', `${PURPOSES}/${ownCode}`);
  const ask = await call(K.b, 'POST', RAISE, { body: { ...request('b'), prpsCode: ownCode, prpsVer: 1 } });
  const publish = await call(K.b, 'POST', `${PURPOSES}/${ownCode}/publish`);
  return [read.status === 404 && ask.status === 404 && publish.status === 404,
    `${read.status}, ${ask.status}, ${publish.status}`];
});
await check('PRP-02', 'Input', 'A request for a superseded version, after a material change', '409 purpose-version-superseded', async () => {
  await call(K.a, 'PUT', `${PURPOSES}/${ownCode}/draft`, { body: purpose(ownCode, { dCats: [
    { code: 'EMAIL', sensitivity: 'ORDINARY', necessary: false }, { code: 'NAME', sensitivity: 'ORDINARY', necessary: false }] }).content });
  await call(K.a, 'POST', `${PURPOSES}/${ownCode}/publish`);
  return is(await call(K.a, 'POST', RAISE, { body: { ...request('a'), prpsCode: ownCode, prpsVer: 1 } }), 409,
    'purpose-version-superseded');
});

// ---------------------------------------------------------------- only the principal decides (V-15)
await check('PRN-01', 'Access control', "A's key approves, declines or withdraws consent on a principal's behalf",
  '403 principal-only each', async () => {
    const approve = await call(K.a, 'POST', `/api/cns-cmd/cns-reqs/${reqA}/approve`, { body: {} });
    const decline = await call(K.a, 'POST', `/api/cns-cmd/cns-reqs/${reqA}/deny`);
    const withdraw = await call(K.a, 'POST', `/api/cns-cmd/cns-arts/${K.grantedA.cnsArtId}/revoke`);
    return [[approve, decline, withdraw].every((r) => r.status === 403 && r.json?.title === 'principal-only'),
      `${approve.status}, ${decline.status}, ${withdraw.status}`];
  });

// ---------------------------------------------------------------- what responses give away
await check('HDR-01', 'Headers', 'API responses: nosniff, no caching', 'X-Content-Type-Options, Cache-Control no-store', async () => {
  const r = await call(K.a, 'GET', LIST);
  const h = r.headers;
  return [h.get('x-content-type-options') === 'nosniff' && /no-store/.test(h.get('cache-control') ?? ''),
    `nosniff=${h.get('x-content-type-options')}, cache-control=${h.get('cache-control')}, frame=${h.get('x-frame-options')}`];
});
await check('HDR-02', 'Headers', 'No server or framework version', 'no Server / X-Powered-By', async () => {
  const r = await send('GET', new URL(LIST, GW), {});
  return [!r.headers.get('server') && !r.headers.get('x-powered-by'), `server=${r.headers.get('server')}, x-powered-by=${r.headers.get('x-powered-by')}`];
});
await check('HDR-03', 'Headers', 'CORS preflight from an unknown origin', 'no Access-Control-Allow-Origin', async () => {
  const r = await send('OPTIONS', new URL(LIST, GW), { Origin: 'https://evil.example', 'Access-Control-Request-Method': 'GET',
    'Access-Control-Request-Headers': 'x-api-key' });
  return [!r.headers.get('access-control-allow-origin'), `${r.status} allow-origin=${r.headers.get('access-control-allow-origin')}`];
});
await check('HDR-04', 'Headers', 'Actuator and gateway internals', 'none answer 200', async () => {
  const paths = ['/actuator', '/actuator/env', '/actuator/heapdump', '/actuator/gateway/routes', '/actuator/prometheus',
    '/api/df-reg/actuator/env', '/api/cns-cmd/actuator/env', '/v3/api-docs', '/swagger-ui.html'];
  const rs = await Promise.all(paths.map((p) => send('GET', new URL(p, GW), {})));
  const open = paths.filter((p, i) => rs[i].status === 200);
  return [open.length === 0, open.length ? `open: ${open.join(', ')}` : rs.map((r) => r.status).join(',')];
});
await check('HDR-05', 'Headers', 'Unknown path', '401 or 404, no stack trace', async () =>
  refused(await call(K.a, 'GET', '/api/cns-qry/does-not-exist')));
await check('HDR-06', 'Headers', 'TRACE', 'not 200', async () => {
  const r = await send('TRACE', new URL(LIST, GW), {}).catch((e) => ({ status: 0, text: e.message }));
  return [r.status !== 200, `${r.status || 'refused by the client'}`];
});
await check('HDR-07', 'Headers', 'Problem details carry no internals', 'no stack trace, class name or SQL', async () => {
  const r = await call(K.a, 'POST', RAISE, { body: { extDpRefTyp: 'NOPE' } });
  return [!STACK.test(r.text) && !/select |insert |org\.|com\.techeazyhq/i.test(r.text), `${r.status} ${r.text.slice(0, 120)}`];
});

// ---------------------------------------------------------------- abuse
await check('ABU-01', 'Abuse', '300 bad-key requests in a burst', 'refused, and the gateway stays up', async () => {
  const bad = { apiKey: `cmk_${randomBytes(24).toString('base64url')}`, signingSecret: 'x' };
  const rs = await Promise.all(Array.from({ length: 300 }, () => call(bad, 'GET', LIST)));
  const counts = rs.reduce((m, r) => ({ ...m, [r.status]: (m[r.status] ?? 0) + 1 }), {});
  const after = await call(K.a, 'GET', LIST);
  return [!counts[200] && !counts[500] && after.status === 200, `${JSON.stringify(counts)}; then a good call ${after.status}`];
});

const failed = results.filter((r) => !r.pass);
console.log(`\n${results.length} checks, ${results.length - failed.length} passed, ${failed.length} failed`);
const out = process.argv.indexOf('--json');
if (out > 0) writeFileSync(process.argv[out + 1], JSON.stringify({ at: new Date().toISOString(), gateway: GW, results }, null, 2));
process.exitCode = failed.length ? 1 : 0;
