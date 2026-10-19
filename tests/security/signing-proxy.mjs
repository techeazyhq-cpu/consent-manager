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

// A scanner can't compute CM1 signatures, so it scans through this proxy: each request it sends is signed afresh with
// one fiduciary's key (ADR-041) and passed to the gateway, so the scan reaches what lies behind authentication, as a
// real integration would. Local stack only. It signs whatever it is sent, so it listens on 127.0.0.1 unless HOST says
// otherwise; tests/security/zap-scan.sh runs it in a container on a private network with the scanner.
//
//   KEYS_FILE=/tmp/cm-keys.json KEY=b PORT=18990 node tests/security/signing-proxy.mjs

import { createServer } from 'node:http';
import { GW, loadKeys, sign } from './lib/cm1.mjs';

const key = loadKeys(process.env.KEYS_FILE)[process.env.KEY ?? 'b'];
const port = Number(process.env.PORT ?? 18990);
const host = process.env.HOST ?? '127.0.0.1';
const DROP = new Set(['host', 'connection', 'content-length', 'transfer-encoding', 'x-api-key', 'authorization',
  'x-signature', 'x-signature-timestamp', 'x-signature-nonce']);
let served = 0;

createServer(async (req, res) => {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  const body = Buffer.concat(chunks);
  const url = new URL(req.url, GW);
  const headers = Object.fromEntries(Object.entries(req.headers).filter(([h]) => !DROP.has(h)));
  Object.assign(headers, { 'X-API-Key': key.apiKey },
    sign(key.signingSecret, req.method, url.pathname, url.search.slice(1), body));
  try {
    const up = await fetch(url, { method: req.method, headers, body: body.length ? body : undefined, redirect: 'manual' });
    const out = Buffer.from(await up.arrayBuffer());
    res.writeHead(up.status, Object.fromEntries([...up.headers].filter(([h]) => !['content-encoding', 'transfer-encoding', 'content-length'].includes(h))));
    res.end(out);
  } catch (e) {
    res.writeHead(502, { 'Content-Type': 'text/plain' }).end(`proxy: ${e.message}`);
  }
  if (++served % 200 === 0) console.log(`${served} requests signed`);
}).listen(port, host, () => console.log(`Signing for ${key.id} on ${host}:${port}, to ${GW}`));
