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

// A consent enforcement point a fiduciary runs next to its own services (ADR-076): one local call, "may I use this consent for
// this purpose?", answered ALLOW, DENY or UNAVAILABLE. It asks the Consent Manager (the live check, ADR-027) and caches what
// it was told for a few seconds; it never answers ALLOW from a cache past that. Optionally (grace mode) it keeps serving a
// consent it has just seen allowed while the Consent Manager cannot be reached, for no longer than a short bound and only
// while the consent token it holds, whose signature it checks itself against the published keys, has not expired.
// Zero dependencies; Node 20+.

import { createPublicKey, verify as cryptoVerify } from 'node:crypto';
import { ConsentClient, Decision } from '../../ai-ingestion-guard/src/index.mjs';

export { Decision };

const b64url = (s) => Buffer.from(s, 'base64url');

/**
 * Checks a consent token the way the Consent Manager's own policy does (ADR-013), offline: RS256 signature against one of the
 * published keys, issuer, expiry, and that it is about this consent, this fiduciary and this purpose, and allows access.
 * @returns {{ok: true, claims: object} | {ok: false, reason: string}}
 */
export function verifyToken(token, jwks, { cnsArtId, purpose, fiduciaryId, now = Date.now() }) {
  const parts = String(token ?? '').split('.');
  if (parts.length !== 3) return { ok: false, reason: 'token-malformed' };
  let header;
  let claims;
  try {
    header = JSON.parse(b64url(parts[0]).toString('utf8'));
    claims = JSON.parse(b64url(parts[1]).toString('utf8'));
  } catch {
    return { ok: false, reason: 'token-malformed' };
  }
  if (header.alg !== 'RS256') return { ok: false, reason: 'token-algorithm' };
  const jwk = (jwks?.keys ?? []).find((k) => k.kid === header.kid);
  if (!jwk) return { ok: false, reason: 'token-key-unknown' };
  let valid = false;
  try {
    valid = cryptoVerify('sha256', Buffer.from(`${parts[0]}.${parts[1]}`), createPublicKey({ key: jwk, format: 'jwk' }), b64url(parts[2]));
  } catch {
    valid = false;
  }
  if (!valid) return { ok: false, reason: 'token-signature' };
  if (claims.iss !== 'cm-cns-cmd-svc') return { ok: false, reason: 'token-issuer' };
  if (typeof claims.exp !== 'number' || claims.exp * 1000 <= now) return { ok: false, reason: 'token-expired' };
  if (claims.cns_art_id !== cnsArtId) return { ok: false, reason: 'token-other-consent' };
  if (claims.purpose_code !== purpose) return { ok: false, reason: 'token-other-purpose' };
  if (fiduciaryId && claims.fiduciary_id !== fiduciaryId) return { ok: false, reason: 'token-other-fiduciary' };
  if (claims.access_type !== 'ALLOW') return { ok: false, reason: 'token-not-allow' };
  return { ok: true, claims };
}

export class Sidecar {
  /**
   * @param {object} o
   * @param {{verify: Function, token: Function}} o.client  a ConsentClient (or one with its verify and token methods)
   * @param {Function} o.jwks  async () => the published key set, as the Consent Manager serves it
   * @param {string} [o.fiduciaryId]  when set, a token for another fiduciary is never accepted offline
   * @param {number} [o.cacheSeconds=5]  how long a live ALLOW or DENY is reused
   * @param {number} [o.graceSeconds=0]  0 turns grace mode off; otherwise how long after a live ALLOW an unreachable
   *                                     Consent Manager may be bridged by the token alone. Never more than the token's own life.
   * @param {string[]} [o.noGracePurposes=[]]  purposes that are never bridged (health, children's data: ADR-027 says fail closed)
   */
  constructor({ client, jwks, fiduciaryId, cacheSeconds = 5, graceSeconds = 0, noGracePurposes = [], now = () => Date.now(), onFault = (e) => console.error(`consent sidecar: ${e.message}`) }) {
    Object.assign(this, { client, jwks, fiduciaryId, cacheSeconds, graceSeconds, noGracePurposes: new Set(noGracePurposes), now, onFault });
    this.cache = new Map();
    this.lastAllowed = new Map();
    this.stats = { live: 0, cached: 0, bridged: 0, unavailable: 0 };
  }

  /** @returns {Promise<{decision: string, basis?: string, reason?: string, staleUntil?: string, retryAfterSeconds?: number}>} */
  async check(cnsArtId, purpose) {
    if (!cnsArtId || !purpose) return { decision: Decision.DENY, reason: 'invalid-request' };
    const key = `${cnsArtId}\n${purpose}`;
    const hit = this.cache.get(key);
    if (hit && hit.until > this.now()) {
      this.stats.cached++;
      return { ...hit.answer, basis: 'cache' };
    }

    let live;
    try {
      live = await this.client.verify(cnsArtId, purpose);
    } catch (e) {
      // the Consent Manager refused this sidecar's key, or the client failed: a fault to fix, and never a yes
      this.stats.unavailable++;
      this.onFault(e);
      return { decision: Decision.UNAVAILABLE, reason: 'sidecar-fault', retryAfterSeconds: 30 };
    }
    if (live.decision === Decision.ALLOW) {
      this.stats.live++;
      const answer = { decision: Decision.ALLOW, basis: 'live' };
      this.cache.set(key, { until: this.now() + this.cacheSeconds * 1000, answer });
      this.lastAllowed.set(key, { at: this.now(), token: await this.#token(cnsArtId) });
      return answer;
    }
    if (live.decision === Decision.DENY) {
      this.stats.live++;
      this.lastAllowed.delete(key);
      const answer = { decision: Decision.DENY, reason: live.reason ?? 'not-allowed', basis: 'live' };
      this.cache.set(key, { until: this.now() + this.cacheSeconds * 1000, answer });
      return answer;
    }
    return this.#bridge(key, cnsArtId, purpose, live);
  }

  async #token(cnsArtId) {
    try {
      return await this.client.token(cnsArtId);
    } catch {
      return null;
    }
  }

  /** The Consent Manager could not be reached: bridge with the token only if grace is on, allowed, recent and verifiable. */
  async #bridge(key, cnsArtId, purpose, live) {
    const unavailable = { decision: Decision.UNAVAILABLE, reason: live.reason ?? 'consent-check-unavailable', retryAfterSeconds: live.retryAfterSeconds ?? 5 };
    const last = this.lastAllowed.get(key);
    if (this.graceSeconds <= 0 || this.noGracePurposes.has(purpose) || !last?.token || this.now() - last.at > this.graceSeconds * 1000) {
      this.stats.unavailable++;
      return unavailable;
    }
    let keys;
    try {
      keys = await this.jwks();
    } catch {
      this.stats.unavailable++;
      return unavailable;
    }
    const checked = verifyToken(last.token, keys, { cnsArtId, purpose, fiduciaryId: this.fiduciaryId, now: this.now() });
    if (!checked.ok) {
      this.stats.unavailable++;
      return unavailable;
    }
    this.stats.bridged++;
    const staleUntil = Math.min(last.at + this.graceSeconds * 1000, checked.claims.exp * 1000);
    return { decision: Decision.ALLOW, basis: 'offline-token', staleUntil: new Date(staleUntil).toISOString() };
  }
}

/** A loop, not a regular expression: a long run of slashes must not cost quadratic time (CodeQL js/polynomial-redos). */
function withoutTrailingSlashes(url) {
  let end = url.length;
  while (end > 0 && url[end - 1] === '/') end--;
  return url.slice(0, end);
}

/** The published keys, fetched through the gateway and kept; a failed refresh keeps serving the last keys that were fetched. */
export function cachedJwks({ gateway, fetchImpl = fetch, now = () => Date.now(), ttlSeconds = 300 }) {
  let keys = null;
  let at = 0;
  return async () => {
    if (keys && now() - at < ttlSeconds * 1000) return keys;
    try {
      const res = await fetchImpl(`${withoutTrailingSlashes(gateway)}/api/cns-cmd/.well-known/jwks.json`);
      if (res.ok) {
        keys = await res.json();
        at = now();
      }
    } catch {
      // keep the keys we have
    }
    if (!keys) throw new Error('the published keys could not be fetched');
    return keys;
  };
}

export { ConsentClient };
