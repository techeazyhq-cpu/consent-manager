#!/usr/bin/env node
// Scripted check of the passwordless browser sign-in (cm-idn-otp-spi, plan §9a): drives cm-realm's login pages the
// way a browser would for cm-shell — the client CM Admin, the DF app and the DP portal all sign in through — reading
// each code from Mailpit, and checks:
//   1. an invalid address is refused on the first page;
//   2. a wrong code is refused;
//   3. the right code ends in an authorization code, and the token carries the account's role (a seeded user);
//   4. an address with no account becomes a DP account by signing in (auto-registration).
//
//   node scripts/verify-otp-login.mjs
//
// Env: KC_BASE (http://localhost:8080), MAILPIT_URL (http://localhost:8025, where cm-ntf-svc's codes land),
//      REDIRECT_URI (http://localhost:5174/, any of cm-shell's redirect URIs).
import { createHash, randomBytes, randomUUID } from 'node:crypto';

const KC = process.env.KC_BASE || 'http://localhost:8080';
const MAILPIT = process.env.MAILPIT_URL || 'http://localhost:8025';
const REDIRECT_URI = process.env.REDIRECT_URI || 'http://localhost:5174/';
const REALM = `${KC}/realms/cm-realm`;

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

/** Signs in as `address`; returns the decoded access token, or null if signing in did not complete. */
async function signIn(address, { tryWrongCode = false } = {}) {
  const go = browser();
  const verifier = randomBytes(32).toString('base64url');
  const challenge = createHash('sha256').update(verifier).digest('base64url');
  const authUrl = `${REALM}/protocol/openid-connect/auth?${new URLSearchParams({
    client_id: 'cm-shell', redirect_uri: REDIRECT_URI, response_type: 'code', scope: 'openid',
    state: randomUUID(), code_challenge: challenge, code_challenge_method: 'S256',
  })}`;
  const identifyPage = await (await go(authUrl)).text();
  const sentAfter = new Date(Date.now() - 1000);
  const codePage = await (await post(go, formAction(identifyPage), { identifier: address })).text();
  if (!codePage.includes('cm-otp-code-form')) {
    check(false, `${address}: code page shown after Send code`);
    return null;
  }
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
  check(done.status === 302 && location.startsWith(REDIRECT_URI) && authCode, `${address}: the right code signs in`);
  if (!authCode) return null;
  const tokens = await (await fetch(`${REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'authorization_code', client_id: 'cm-shell', code: authCode, redirect_uri: REDIRECT_URI, code_verifier: verifier }),
  })).json();
  return JSON.parse(Buffer.from(tokens.access_token.split('.')[1], 'base64url').toString());
}

const go = browser();
const firstPage = await (await go(`${REALM}/protocol/openid-connect/auth?${new URLSearchParams({ client_id: 'cm-shell', redirect_uri: REDIRECT_URI, response_type: 'code', scope: 'openid', code_challenge: 'x'.repeat(43), code_challenge_method: 'S256' })}`)).text();
const refused = await (await post(go, formAction(firstPage), { identifier: 'not an address' })).text();
check(refused.includes('Enter a valid email address'), 'an invalid address is refused');

const admin = await signIn('admin@cm.local', { tryWrongCode: true });
check(admin?.roles?.includes('CM_ADMIN'), 'the seeded admin signs in with a code and keeps CM_ADMIN');

const newcomer = `dp-${Date.now()}@example.com`;
const created = await signIn(newcomer);
check(created?.roles?.includes('DP') && created?.email === newcomer, `${newcomer} became a DP account by signing in`);

console.log(failures === 0 ? '\nAll OTP sign-in checks passed.' : `\n${failures} check(s) failed.`);
process.exit(failures === 0 ? 0 : 1);
