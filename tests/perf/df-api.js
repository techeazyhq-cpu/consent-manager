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

// Load tests of the Data Fiduciary API through the gateway, signing every request as an integration would (CM1,
// ADR-041). Uses fiduciary B from tests/security/setup-keys.mjs (PREMIUM: 500 requests a second).
//
//   SCENARIO=smoke | mix | auth | ramp   (see SCENARIOS below; RATE and DURATION tune mix and auth)
//   docker run --rm -i --add-host host.docker.internal:host-gateway -v "$KEYS_FILE:/keys.json:ro" \
//     -v "$PWD/tests/perf:/perf:ro" -e SCENARIO=mix grafana/k6:1.3.0 run /perf/df-api.js
//
// What each request is:
//   verify   GET  /api/cns-qry/cns-arts/{id}/verify   the hot path: PEP, OPA, the live revocation check
//   token    POST /api/cns-cmd/cns-arts/{id}/token    a fresh consent token (signing, the database)
//   read     GET  /api/cns-qry/cns-arts/{id}          a read-model lookup
//   raise    POST /api/cns-cmd/cns-reqs               a new request: write, outbox, notification
//   jwks     GET  /api/cns-cmd/.well-known/jwks.json  public, no authentication: the gateway's floor

import http from 'k6/http';
import crypto from 'k6/crypto';
import { check } from 'k6';

const K = JSON.parse(open(__ENV.KEYS ?? '/keys.json'));
const BASE = __ENV.BASE ?? 'http://host.docker.internal:9000';
const RATE = Number(__ENV.RATE ?? 50);
const DURATION = __ENV.DURATION ?? '2m';
const key = K.b;
const granted = K.grantedB;

const arrival = (exec, rate, extra = {}) => ({ executor: 'constant-arrival-rate', exec, rate, timeUnit: '1s',
  duration: DURATION, preAllocatedVUs: Math.max(10, rate), maxVUs: rate * 4, ...extra });

const SCENARIOS = {
  // one of each, to check the wiring before a run
  smoke: { smoke: { executor: 'per-vu-iterations', exec: 'everything', vus: 1, iterations: 5 } },
  // the expected traffic: mostly checks before using data, some new requests
  mix: {
    verify: arrival('verify', Math.round(RATE * 0.6)),
    read: arrival('read', Math.round(RATE * 0.2)),
    token: arrival('token', Math.round(RATE * 0.1)),
    raise: arrival('raise', Math.max(1, Math.round(RATE * 0.1))),
  },
  // the same read, with an API key and signature and with an OAuth bearer token, side by side
  auth: {
    apikey: arrival('read', RATE),
    bearer: arrival('readBearer', RATE),
    jwks: arrival('jwks', RATE),
  },
  // rising load until it breaks: where the knee is
  ramp: {
    ramp: { executor: 'ramping-arrival-rate', exec: 'mixOne', startRate: 10, timeUnit: '1s', preAllocatedVUs: 50,
      maxVUs: 600, stages: [{ target: 50, duration: '1m' }, { target: 150, duration: '1m' }, { target: 300, duration: '1m' },
        { target: 450, duration: '1m' }] },
  },
};

export const options = {
  scenarios: SCENARIOS[__ENV.SCENARIO ?? 'smoke'],
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{op:verify}': ['p(95)<300'],
    'http_req_duration{op:read}': ['p(95)<300'],
    'http_req_duration{op:token}': ['p(95)<500'],
    'http_req_duration{op:raise}': ['p(95)<800'],
  },
};

function hex(buffer) {
  return Array.from(new Uint8Array(buffer), (b) => b.toString(16).padStart(2, '0')).join('');
}

function signed(method, path, query, body) {
  const ts = Math.floor(Date.now() / 1000).toString();
  const nonce = hex(crypto.randomBytes(16));
  const text = ['CM1-HMAC-SHA256', ts, nonce, method, path, query, crypto.sha256(body, 'hex')].join('\n');
  return { 'X-API-Key': key.apiKey, 'X-Signature-Timestamp': ts, 'X-Signature-Nonce': nonce,
    'X-Signature': crypto.hmac('sha256', key.signingSecret, text, 'hex') };
}

function send(op, method, path, query = '', body = '', extra = {}) {
  const headers = { ...signed(method, path, query, body), ...extra };
  if (body) headers['Content-Type'] = 'application/json';
  const res = http.request(method, `${BASE}${path}${query ? `?${query}` : ''}`, body || null, { headers, tags: { op } });
  check(res, { [`${op} ok`]: (r) => r.status >= 200 && r.status < 300 });
  return res;
}

// a consent token lives minutes: each VU refreshes its own when it is near expiry
let cnsTkn;
let cnsTknExp = 0;
function consentToken() {
  if (!cnsTkn || Date.now() > cnsTknExp - 30_000) {
    const r = send('token', 'POST', `/api/cns-cmd/cns-arts/${granted.cnsArtId}/token`);
    cnsTkn = r.json('cnsTkn');
    cnsTknExp = Date.parse(r.json('exp'));
  }
  return cnsTkn;
}

export function verify() {
  send('verify', 'GET', `/api/cns-qry/cns-arts/${granted.cnsArtId}/verify`, `purpose=${granted.purpose}`, '',
    { 'X-Cns-Tkn': consentToken() });
}

export function token() {
  send('token', 'POST', `/api/cns-cmd/cns-arts/${granted.cnsArtId}/token`);
}

export function read() {
  send('read', 'GET', `/api/cns-qry/cns-arts/${granted.cnsReqId}`);
}

export function readBearer() {
  const res = http.get(`${BASE}/api/cns-qry/cns-arts/${granted.cnsReqId}`,
    { headers: { Authorization: `Bearer ${K.tokenB}` }, tags: { op: 'read-bearer' } });
  check(res, { 'read-bearer ok': (r) => r.status === 200 });
}

export function jwks() {
  const res = http.get(`${BASE}/api/cns-cmd/.well-known/jwks.json`, { tags: { op: 'jwks' } });
  check(res, { 'jwks ok': (r) => r.status === 200 });
}

export function raise() {
  const body = JSON.stringify({ extDpRefTyp: 'EMAIL', extDpRefVal: `perf-${__VU}-${__ITER}@example.test`,
    prpsCode: 'ORDER_UPDATES', prpsVer: K.prpsVerA ?? 1 });
  send('raise', 'POST', '/api/cns-cmd/cns-reqs', '', body);
}

export function mixOne() {
  const r = Math.random();
  if (r < 0.6) verify();
  else if (r < 0.8) read();
  else if (r < 0.9) token();
  else raise();
}

export function everything() {
  verify();
  read();
  token();
  raise();
  jwks();
}
