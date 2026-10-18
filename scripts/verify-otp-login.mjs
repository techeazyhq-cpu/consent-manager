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

// Scripted check of the passwordless browser sign-in (cm-idn-otp-spi, plan §9a): drives each realm's login pages (ADR-035) the
// way a browser would for each frontend's client (cm-dp-portal, cm-df-app, cm-cm-admin), reading each code from
// Mailpit, and checks:
//   0. each client's sign-in page shows its own audience theme (cm-idn-theme), and keeps it on the code page;
//   1. an invalid address is refused on the first page;
//   2. a wrong code is refused;
//   3. the right code ends in an authorization code, and the token carries the account's role (a seeded user);
//   4. an address with no account becomes a DP account by signing in (auto-registration).
//
//   node scripts/verify-otp-login.mjs
//
// Env: KC_BASE (http://localhost:8080), MAILPIT_URL (http://localhost:8025, where cm-ntf-svc's codes land).
import { createHash, randomBytes, randomUUID } from 'node:crypto';

const KC = process.env.KC_BASE || 'http://localhost:8080';
const MAILPIT = process.env.MAILPIT_URL || 'http://localhost:8025';
const realmUrl = (client) => `${KC}/realms/${client.realm}`;

/** Each frontend's client and realm, where it sends people back to, and the audience its login theme shows. */
const CLIENTS = {
  dpPortal: { realm: 'cm-dp', clientId: 'cm-dp-portal', redirectUri: 'http://localhost:5174/', audience: 'dp' },
  dfApp: { realm: 'cm-df', clientId: 'cm-df-app', redirectUri: 'http://localhost:5175/', audience: 'df' },
  cmAdmin: { realm: 'cm-staff', clientId: 'cm-cm-admin', redirectUri: 'http://localhost:5176/', audience: 'admin' },
};

let failures = 0;
const check = (ok, what) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}`);
  if (!ok) failures++;
};

/** A minimal browser: follows nothing by itself, keeps cookies per sign-in. */
function browser() {
  const cookies = new Map();
  return async (url, init = {}) => {
    const res = await fetch(url, {
      ...init,
      redirect: 'manual',
      headers: { ...init.headers, Cookie: [...cookies].map(([k, v]) => `${k}=${v}`).join('; ') },
    });
    for (const line of res.headers.getSetCookie()) {
      const [pair] = line.split(';');
      const eq = pair.indexOf('=');
      cookies.set(pair.slice(0, eq).trim(), pair.slice(eq + 1));
    }
    return res;
  };
}

const formAction = (html) => {
  const match = html.match(/<form[^>]*id="(cm-otp-identify-form|cm-otp-code-form)"[^>]*action="([^"]+)"/);
  if (!match) throw new Error(`No sign-in form on the page:\n${html.slice(0, 500)}`);
  return match[2].replaceAll('&amp;', '&');
};

const post = (go, url, fields) =>
  go(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(fields) });

async function latestCode(address, after) {
  for (let attempt = 0; attempt < 20; attempt++) {
    const res = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}&limit=1`);
    const [message] = (await res.json()).messages ?? [];
    if (message && new Date(message.Created) >= after) {
      const code = message.Snippet.match(/\b\d{6}\b/)?.[0];
      if (code) return code;
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`No code reached ${address} in Mailpit`);
}

const authorizationUrl = (client, challenge) => `${realmUrl(client)}/protocol/openid-connect/auth?${new URLSearchParams({
  client_id: client.clientId, redirect_uri: client.redirectUri, response_type: 'code', scope: 'openid',
  state: randomUUID(), code_challenge: challenge, code_challenge_method: 'S256',
})}`;

const audienceOf = (html) => html.match(/<body[^>]*data-audience="([^"]+)"/)?.[1];

/** Signs in as `address` through `client`; returns the decoded access token, or null if signing in did not complete. */
async function signIn(address, client, { tryWrongCode = false } = {}) {
  const go = browser();
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const identifyPage = await (await go(authorizationUrl(client, challenge))).text();
  const sentAfter = new Date(Date.now() - 1000);
  const codePage = await (await post(go, formAction(identifyPage), { identifier: address })).text();
  if (!codePage.includes('cm-otp-code-form')) {
    check(false, `${address}: code page shown after Send code`);
    return null;
  }
  check(audienceOf(codePage) === client.audience,
    `${client.clientId}: the code page keeps the ${client.audience} theme`);
  check(identifyPage.includes('class="cm-intro"') && !codePage.includes('class="cm-intro"'),
    `${client.clientId}: the audience intro shows on the first step only`);
  let action = formAction(codePage);
  const code = await latestCode(address, sentAfter);
  if (tryWrongCode) {
    const wrong = code === '000000' ? '000001' : '000000';
    const retryPage = await (await post(go, action, { code: wrong })).text();
    check(retryPage.includes('That code is not right'), `${address}: a wrong code is refused`);
    action = formAction(retryPage);
  }
  const done = await post(go, action, { code });
  const location = done.headers.get('location') ?? '';
  const authCode = new URL(location, KC).searchParams.get('code');
  check(done.status === 302 && location.startsWith(client.redirectUri) && authCode,
    `${address}: the right code signs in`);
  if (!authCode) return null;
  const tokens = await (await fetch(`${realmUrl(client)}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code', client_id: client.clientId, code: authCode,
      redirect_uri: client.redirectUri, code_verifier: verifier,
    }),
  })).json();
  return JSON.parse(Buffer.from(tokens.access_token.split('.')[1], 'base64url').toString());
}

for (const client of Object.values(CLIENTS)) {
  const signInPage = await (await browser()(authorizationUrl(client, 'x'.repeat(43)))).text();
  check(audienceOf(signInPage) === client.audience,
    `${client.clientId}: the sign-in page shows the ${client.audience} theme`);
}

const go = browser();
const firstPage = await (await go(authorizationUrl(CLIENTS.dpPortal, 'x'.repeat(43)))).text();
const refused = await (await post(go, formAction(firstPage), { identifier: 'not an address' })).text();
check(refused.includes('Enter a valid email address'), 'an invalid address is refused');
check(audienceOf(refused) === 'dp', 'the refusal stays on the dp theme');

const admin = await signIn('admin@cm.local', CLIENTS.cmAdmin, { tryWrongCode: true });
check(admin?.roles?.includes('CM_ADMIN'), 'the seeded admin signs in with a code and keeps CM_ADMIN');

const newcomer = `dp-${Date.now()}@example.com`;
const created = await signIn(newcomer, CLIENTS.dpPortal);
check(created?.roles?.includes('DP') && created?.email === newcomer, `${newcomer} became a DP account by signing in`);

/** Proves an address, then expects the realm to refuse it: no account there, and none created (ADR-035). */
async function refusedWithNoAccount(address, client) {
  const go = browser();
  const identifyPage = await (await go(authorizationUrl(client, 'x'.repeat(43)))).text();
  const sentAfter = new Date(Date.now() - 1000);
  const codePage = await (await post(go, formAction(identifyPage), { identifier: address })).text();
  const code = await latestCode(address, sentAfter);
  const done = await post(go, formAction(codePage), { code });
  const page = done.status === 200 ? await done.text() : '';
  check(done.status === 200 && page.includes('There is no account for that address'),
    `${client.clientId}: ${address} gets no account, and none is made`);
}

// Only data principals sign themselves up; a principal's account doesn't exist in the other realms.
await refusedWithNoAccount(`stranger-${Date.now()}@example.com`, CLIENTS.cmAdmin);
await refusedWithNoAccount(`stranger-${Date.now()}@example.com`, CLIENTS.dfApp);
await refusedWithNoAccount(newcomer, CLIENTS.cmAdmin);

console.log(failures === 0 ? '\nAll OTP sign-in checks passed.' : `\n${failures} check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
