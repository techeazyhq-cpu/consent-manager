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

// Sign-in code abuse limits end to end on the local stack (ADR-061), through the real sign-in pages: every check opens
// fresh sign-in sessions, as an attacker would, since the limits that matter are the ones across sign-ins.
//   - codes to one address stop at five an hour, however many sign-ins ask, and the mail inbox agrees
//   - an address with an account and one without are refused in exactly the same words
//   - a +tag doesn't make a new mailbox
//   - wrong guesses across sign-ins use up one budget, and then not even a right code is accepted
// It sends about 17 codes, to made-up addresses, and leaves nothing to clean up (the cache entries expire in a day).
//
//   node tests/security/e2e-otp-abuse.mjs

import { createHash, randomBytes } from 'node:crypto';
import { KC, MAILPIT, dpSignIn } from './lib/cm1.mjs';

const REALM = `${KC}/realms/cm-dp/protocol/openid-connect`;
const CLIENT = { clientId: 'cm-dp-portal', redirectUri: 'http://localhost:5174/' };

const MESSAGES = {
  codes: 'Too many codes have been sent to this address lately',
  guesses: 'Too many wrong codes have been entered for this address lately',
  network: 'Too many sign-in codes have been requested from your network',
  wrong: 'That code is not right',
  attempts: 'Too many wrong codes. Start again',
};
const which = (html) => Object.entries(MESSAGES).find(([, text]) => html.includes(text))?.[0] ?? null;

const expect = (ok, what, detail) => {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${what}${detail !== undefined ? `  ->  ${detail}` : ''}`);
  if (!ok) process.exit(1);
};

/** One fresh sign-in, as a browser would open it: its own cookies, its own session in Keycloak. */
async function signInPage() {
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
  const post = (url, fields) => go(url, { method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams(fields) });
  const action = (html, id) => html.match(new RegExp(`<form[^>]*id="${id}"[^>]*action="([^"]+)"`))?.[1].replaceAll('&amp;', '&');
  const verifier = randomBytes(32).toString('base64url');
  const auth = `${REALM}/auth?${new URLSearchParams({ client_id: CLIENT.clientId, redirect_uri: CLIENT.redirectUri,
    response_type: 'code', scope: 'openid', state: randomBytes(8).toString('hex'),
    code_challenge: createHash('sha256').update(verifier).digest('base64url'), code_challenge_method: 'S256' })}`;
  const identifyHtml = await (await go(auth)).text();
  let codeAction = null;
  return {
    /** Asks for a code: {sent: true}, or {sent: false, why} with the refusal's kind. */
    async requestCode(address) {
      const html = await (await post(action(identifyHtml, 'cm-otp-identify-form'), { identifier: address })).text();
      codeAction = action(html, 'cm-otp-code-form');
      return codeAction ? { sent: true } : { sent: false, why: which(html), html };
    },
    /** Types a code: {signedIn: true}, or what the page said. */
    async enterCode(code) {
      const res = await post(codeAction, { code });
      if (res.status >= 300 && res.status < 400 && (res.headers.get('location') ?? '').startsWith(CLIENT.redirectUri)) {
        return { signedIn: true };
      }
      const html = await res.text();
      // every page carries a fresh session token in its form's address: the next guess must use that one
      codeAction = action(html, 'cm-otp-code-form') ?? codeAction;
      return { signedIn: false, why: which(html) };
    },
  };
}

const inbox = async (address) => (await (await fetch(
  `${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:"${address}"`)}&limit=50`)).json()).messages ?? [];
const latestCode = async (address) => {
  for (let i = 0; i < 30; i++) {
    const [m] = await inbox(address);
    const code = m?.Snippet.match(/\b\d{6}\b/)?.[0];
    if (code) return code;
    await new Promise((ok) => setTimeout(ok, 500));
  }
  throw new Error(`no code for ${address}`);
};
const tag = randomBytes(3).toString('hex');
const wait = (ms) => new Promise((ok) => setTimeout(ok, ms));

// ---- 1. bombing an address with a fresh sign-in each time
const unknown = `abuse-unknown-${tag}@example.test`;
for (let i = 1; i <= 5; i++) {
  const r = await (await signInPage()).requestCode(unknown);
  expect(r.sent, `code ${i} of 5 to an address nobody has`, r.why);
}
const sixth = await (await signInPage()).requestCode(unknown);
expect(!sixth.sent && sixth.why === 'codes', 'the sixth, from a fresh sign-in, is refused', sixth.why);
await wait(1500);
expect((await inbox(unknown)).length === 5, 'and only five emails reached the inbox', (await inbox(unknown)).length);

// ---- 2. the same words for an address that has an account
const known = `abuse-known-${tag}@example.test`;
await dpSignIn(known); // the account, and the first code
for (let i = 2; i <= 5; i++) {
  const r = await (await signInPage()).requestCode(known);
  expect(r.sent, `code ${i} of 5 to an address with an account`, r.why);
}
const knownSixth = await (await signInPage()).requestCode(known);
expect(!knownSixth.sent && knownSixth.why === 'codes', 'the sixth is refused for them too', knownSixth.why);
expect(knownSixth.html.replace(/\s+/g, ' ').includes(MESSAGES.codes) && sixth.html.replace(/\s+/g, ' ').includes(MESSAGES.codes),
  'in the same words: a refusal says nothing about who has an account');

// ---- 3. a +tag is the same mailbox
const mailbox = `abuse-plus-${tag}`;
for (let i = 1; i <= 5; i++) {
  const r = await (await signInPage()).requestCode(`${mailbox}+${i}@example.test`);
  expect(r.sent, `code ${i} of 5 to ${mailbox}+${i}`, r.why);
}
const aliased = await (await signInPage()).requestCode(`${mailbox}+6@example.test`);
expect(!aliased.sent && aliased.why === 'codes', 'a sixth through another +tag is refused', aliased.why);
const plain = await (await signInPage()).requestCode(`${mailbox}@example.test`);
expect(!plain.sent && plain.why === 'codes', 'and so is the plain address', plain.why);

// ---- 4. wrong guesses across sign-ins share one budget of ten
const guessed = `abuse-guess-${tag}@example.test`;
const first = await signInPage();
expect((await first.requestCode(guessed)).sent, 'a code is sent to a new address');
for (let i = 1; i <= 5; i++) {
  const r = await first.enterCode('000000');
  expect(!r.signedIn && (r.why === 'wrong' || r.why === 'attempts'), `wrong guess ${i} of the first code`, r.why);
}
const second = await signInPage();
expect((await second.requestCode(guessed)).sent, 'a fresh sign-in gets a fresh code, as before');
for (let i = 6; i <= 9; i++) {
  const r = await second.enterCode('000000');
  expect(!r.signedIn && r.why === 'wrong', `wrong guess ${i} (the budget is ten)`, r.why);
}
const tenth = await second.enterCode('000000');
expect(!tenth.signedIn, 'the tenth wrong guess is still just wrong', tenth.why);
const real = await latestCode(guessed);
const right = await second.enterCode(real);
expect(!right.signedIn && right.why === 'guesses', 'now not even the right code is accepted', right.why);
const third = await (await signInPage()).requestCode(guessed);
expect(!third.sent && third.why === 'guesses', 'and no new code is sent for that address', third.why);
const other = await (await signInPage()).requestCode(`abuse-other-${tag}@example.test`);
expect(other.sent, 'another address is not affected');

console.log('All checks passed.');
