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

// What the security and performance kits share: CM1-HMAC-SHA256 signing (ADR-041), the DF passwordless sign-in, and
// the keys file the setup writes. Keys never go to stdout; they live in a file outside the repository.

import { createHash, createHmac, randomBytes, randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

export const GW = process.env.GW_BASE || 'http://localhost:9000';
export const KC = process.env.KC_BASE || 'http://localhost:8080';
// sign-in codes go out through notification-service to the one Mailpit in the common project (ADR-053)
export const MAILPIT = process.env.MAILPIT_URL || 'http://localhost:8025';

export const sha256Hex = (bytes) => createHash('sha256').update(bytes ?? '').digest('hex');

/** The signature headers for one request. `over` overrides any part of what is signed, to forge mismatches. */
export function sign(secret, method, path, query, body, over = {}) {
  const ts = over.ts ?? Math.floor(Date.now() / 1000).toString();
  const nonce = over.nonce ?? randomBytes(16).toString('hex');
  const text = ['CM1-HMAC-SHA256', ts, nonce, (over.method ?? method).toUpperCase(), over.path ?? path,
    over.query ?? query ?? '', over.bodyHash ?? sha256Hex(body)].join('\n');
  const sig = createHmac('sha256', over.secret ?? secret).update(text).digest('hex');
  return { 'X-Signature-Timestamp': ts, 'X-Signature-Nonce': nonce, 'X-Signature': sig };
}

/** Sends a request with key `k` ({apiKey, signingSecret}), signed unless `unsigned`; returns {status, body, headers}. */
export async function call(k, method, pathAndQuery, { body, unsigned, over, headers = {}, signHeaders } = {}) {
  const url = new URL(pathAndQuery, GW);
  const raw = body === undefined ? '' : typeof body === 'string' ? body : JSON.stringify(body);
  const h = { ...headers };
  if (k) h['X-API-Key'] = k.apiKey;
  if (raw) h['Content-Type'] ??= 'application/json';
  if (k && !unsigned) Object.assign(h, signHeaders ?? sign(k.signingSecret, method, url.pathname, url.search.slice(1), raw, over));
  return send(method, url, h, raw);
}

export async function send(method, url, headers, raw) {
  const res = await fetch(url, { method, headers, body: raw || undefined, redirect: 'manual' });
  const text = await res.text();
  let json;
  try { json = JSON.parse(text); } catch { json = undefined; }
  return { status: res.status, text, json, headers: res.headers };
}

export const loadKeys = (file) => JSON.parse(readFileSync(file, 'utf8'));

const DF_APP = { realm: 'cm-df', clientId: 'cm-df-app', redirectUri: 'http://localhost:5175/' };
const DP_PORTAL = { realm: 'cm-dp', clientId: 'cm-dp-portal', redirectUri: 'http://localhost:5174/' };

/** A fiduciary's console user, signed in with the emailed one-time code; returns the access token. */
export const dfSignIn = (address) => signIn(address, DF_APP);

/** A data principal, signed in to the portal (an unknown address becomes an account); returns the access token. */
export const dpSignIn = (address) => signIn(address, DP_PORTAL);

/** Signs in through `client`'s login pages as a browser would, reading the code from Mailpit. */
async function signIn(address, { realm: realmName, clientId, redirectUri }) {
  const cookies = new Map();
  const go = async (url, init = {}) => {
    const res = await fetch(url, { ...init, redirect: 'manual',
      headers: { ...init.headers, Cookie: [...cookies].map(([n, v]) => `${n}=${v}`).join('; ') } });
    for (const line of res.headers.getSetCookie()) {
      const [pair] = line.split(';');
      const eq = pair.indexOf('=');
      cookies.set(pair.slice(0, eq).trim(), pair.slice(eq + 1));
    }
    return res;
  };
  const form = (html) => html.match(/<form[^>]*id="(cm-otp-identify-form|cm-otp-code-form)"[^>]*action="([^"]+)"/)?.[2]
    .replaceAll('&amp;', '&');
  const post = (url, fields) => go(url, { method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(fields) });
  const realm = `${KC}/realms/${realmName}/protocol/openid-connect`;
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const auth = `${realm}/auth?${new URLSearchParams({ client_id: clientId, redirect_uri: redirectUri,
    response_type: 'code', scope: 'openid', state: randomUUID(), code_challenge: challenge,
    code_challenge_method: 'S256' })}`;
  const identify = await (await go(auth)).text();
  const after = new Date(Date.now() - 1000);
  const codePage = await (await post(form(identify), { identifier: address })).text();
  if (!form(codePage)) throw new Error(`${address}: no code page`);
  let code;
  for (let i = 0; i < 30 && !code; i++) {
    const r = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}&limit=1`);
    const [m] = (await r.json()).messages ?? [];
    if (m && new Date(m.Created) >= after) code = m.Snippet.match(/\b\d{6}\b/)?.[0];
    if (!code) await new Promise((ok) => setTimeout(ok, 500));
  }
  if (!code) throw new Error(`${address}: no code in Mailpit`);
  const done = await post(form(codePage), { code });
  const authCode = new URL(done.headers.get('location') ?? '', KC).searchParams.get('code');
  if (!authCode) throw new Error(`${address}: sign-in did not complete`);
  const tokens = await (await fetch(`${realm}/token`, { method: 'POST', body: new URLSearchParams({
    grant_type: 'authorization_code', client_id: clientId, code: authCode, redirect_uri: redirectUri,
    code_verifier: verifier }) })).json();
  return tokens.access_token;
}
