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

// Errors as a caller sees them, through the gateway on the local stack (ADR-062): each comes from a different place (a
// service's business rule, the gateway's own filter, the API-key check in cm-df-reg-svc, input that can't be read), and
// every one must be the same shape: type and code from the dictionary, its title, its status, whether to retry, and the
// correlation id the response header carries. Checked against the dictionary itself, so a title changed there is not a
// failure here, but a response that disagrees with it is.
//
//   node tests/security/e2e-errors.mjs

import { readFileSync } from 'node:fs';
import { GW, dfSignIn, dpSignIn } from './lib/cm1.mjs';

const dictionary = new Map(JSON.parse(readFileSync(
  new URL('../../backend/libs/cm-web-kit/src/main/resources/cm-error-dictionary.json', import.meta.url), 'utf8')).entries
  .map((e) => [e.code, e]));
const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) process.exit(1);
};
const call = async (method, path, { token, headers = {}, body, raw } = {}) => {
  const res = await fetch(new URL(path, GW), { method, redirect: 'manual', body: raw ?? (body ? JSON.stringify(body) : undefined),
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(body || raw ? { 'Content-Type': 'application/json' } : {}), ...headers } });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* not JSON */ }
  return { status: res.status, json, text, type: res.headers.get('content-type') ?? '', correlation: res.headers.get('x-correlation-id') };
};

/** The response is a problem exactly as the dictionary says it should be. */
function isProblem(r, code, what) {
  const e = dictionary.get(code);
  const p = r.json ?? {};
  expect(!!e, `${what}: ${code} is in the dictionary`);
  expect(r.status === e.status && p.status === e.status, `${what}: status ${e.status}`, `${r.status}/${p.status}`);
  expect(r.type.startsWith('application/problem+json'), `${what}: problem+json`, r.type);
  expect(p.type === `urn:cm:problem:${code}` && p.code === code, `${what}: type and code`, `${p.type} / ${p.code}`);
  expect(p.title === e.title, `${what}: the dictionary's title`, p.title);
  expect(p.retryable === e.retryable, `${what}: retryable is ${e.retryable}`);
  expect(typeof p.detail === 'string' && p.detail.length > 0 || code === 'aud-forbidden', `${what}: says what was wrong`);
  expect(!r.correlation || p.correlationId === r.correlation, `${what}: the body's correlation id is the header's`, p.correlationId);
}

const dp = await dpSignIn(`errors-${Math.random().toString(16).slice(2, 8)}@example.test`);
const df = await dfSignIn(process.env.DF_A_OWNER || 'batch-owner@example.test');

// a business rule in a service
isProblem(await call('POST', '/api/dsr/me/requests', { token: dp, body: { kind: 'RELAY', cnsReqId: 'x', right: 'FORGET' } }),
  'invalid-right', 'a rule in cm-dsr-svc');
isProblem(await call('GET', '/api/dsr/df/requests/00000000-0000-0000-0000-000000000000', { token: df }),
  'dp-request-not-found', 'a not-found in cm-dsr-svc');
isProblem(await call('POST', '/api/cns-cmd/dp-links', { token: dp, body: { extDpRefTyp: 'EMAIL', extDpRefVal: 'someone-else@example.test' } }),
  'contact-not-yours', 'a refusal in cm-cns-cmd-svc');

// the gateway's own filter, and a code that comes back from another service
isProblem(await call('GET', '/api/cns-qry/cns-arts', { headers: { 'X-API-Key': 'cmk_nonsense', Authorization: `Bearer ${df}` } }),
  'ambiguous-credentials', "the gateway's own filter");
const badKey = await call('GET', '/api/cns-qry/cns-arts', { headers: { 'X-API-Key': 'cmk_nonsensenonsensenonsense' } });
isProblem(badKey, 'api-key-invalid', 'a refusal from the API-key check, carried through the gateway');

// input that can't be read: nothing of the parser's words comes back
const garbled = await call('POST', '/api/dsr/me/requests', { token: dp, raw: '{"kind": ' });
isProblem(garbled, 'malformed-request', 'unreadable input');
expect(!/Unexpected|JSON parse|Jackson|line \d/.test(garbled.text), 'and none of the parser\'s own words', garbled.text.slice(0, 120));

// a correlation id the caller sends comes back in the body
const mine = `e2e-${Math.random().toString(16).slice(2, 10)}`;
const echoed = await call('POST', '/api/dsr/me/requests', { token: dp, headers: { 'X-Correlation-Id': mine },
  body: { kind: 'RELAY', cnsReqId: 'x', right: 'FORGET' } });
expect(echoed.json?.correlationId === mine && echoed.correlation === mine, 'a correlation id sent in comes back in the body and the header', echoed.json?.correlationId);

console.log('All checks passed.');
