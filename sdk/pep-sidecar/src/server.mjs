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

// The sidecar's HTTP face (ADR-076). Listens on localhost by default: it answers for whoever can reach it, so it is for the
// fiduciary's own services on the same host or pod, not for the network.
//
//   POST /v1/check   {"cnsArtId": "...", "purpose": "ORDER_UPDATES"}  ->  200 {"decision": "ALLOW" | "DENY" | "UNAVAILABLE", ...}
//   GET  /healthz    200 {"status": "UP", "stats": {...}}
//
// UNAVAILABLE is answered 503 with Retry-After: the caller must not read it as a yes or as a no. DENY is 200: it is an answer.
//
// Environment: CM_GATEWAY, CM_API_KEY, CM_SIGNING_SECRET (required); CM_FIDUCIARY_ID (recommended), PEP_PORT (8484),
// PEP_HOST (127.0.0.1), PEP_CACHE_SECONDS (5), PEP_GRACE_SECONDS (0 = off), PEP_NO_GRACE_PURPOSES (comma-separated),
// PEP_MODE (live, the default, or list: answer from the consent token and the status list, ADR-077),
// PEP_STATUS_LIST_ORIGIN (the one origin a token's status list may be fetched from; default: the origin of CM_GATEWAY).

import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { ConsentClient, Decision, Sidecar, cachedJwks, fetchStatusList } from './sidecar.mjs';

const MAX_BODY = 4096;

export function handler(sidecar) {
  return async (req, res) => {
    const send = (status, body, headers = {}) => {
      res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', ...headers });
      res.end(JSON.stringify(body));
    };
    if (req.method === 'GET' && req.url === '/healthz') return send(200, { status: 'UP', stats: sidecar.stats });
    if (req.method !== 'POST' || req.url !== '/v1/check') return send(404, { error: 'not-found' });
    let raw = '';
    for await (const chunk of req) {
      raw += chunk;
      if (raw.length > MAX_BODY) return send(413, { error: 'too-large' });
    }
    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return send(400, { decision: Decision.DENY, reason: 'invalid-request' });
    }
    const answer = await sidecar.check(body?.cnsArtId, body?.purpose);
    if (answer.decision === Decision.UNAVAILABLE) return send(503, answer, { 'Retry-After': String(answer.retryAfterSeconds ?? 5) });
    return send(200, answer);
  };
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  const env = process.env;
  for (const k of ['CM_GATEWAY', 'CM_API_KEY', 'CM_SIGNING_SECRET']) {
    if (!env[k]) {
      console.error(`${k} is required`);
      process.exit(2);
    }
  }
  const client = new ConsentClient({ gateway: env.CM_GATEWAY, apiKey: env.CM_API_KEY, signingSecret: env.CM_SIGNING_SECRET });
  const sidecar = new Sidecar({
    client,
    jwks: cachedJwks({ gateway: env.CM_GATEWAY }),
    fiduciaryId: env.CM_FIDUCIARY_ID || undefined,
    cacheSeconds: Number(env.PEP_CACHE_SECONDS ?? 5),
    graceSeconds: Number(env.PEP_GRACE_SECONDS ?? 0),
    mode: env.PEP_MODE === 'list' ? 'list' : 'live',
    fetchList: fetchStatusList(),
    listOrigin: env.PEP_STATUS_LIST_ORIGIN || new URL(env.CM_GATEWAY).origin,
    noGracePurposes: (env.PEP_NO_GRACE_PURPOSES ?? '').split(',').map((s) => s.trim()).filter(Boolean),
  });
  const port = Number(env.PEP_PORT ?? 8484);
  const host = env.PEP_HOST ?? '127.0.0.1';
  createServer(handler(sidecar)).listen(port, host, () => console.log(`consent sidecar on http://${host}:${port}`));
}
