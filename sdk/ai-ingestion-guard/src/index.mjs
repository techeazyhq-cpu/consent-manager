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

// A consent guard for AI ingestion pipelines (ADR-069): the check a fiduciary makes before personal data is turned into
// embeddings, and the clean-up it makes when the person's consent ends. Zero dependencies; Node 20+.
//
//   const client = new ConsentClient({ gateway, apiKey, signingSecret });
//   const guard = new IngestionGuard({ client, store, embed, purpose: 'SUPPORT_ASSISTANT' });
//   await guard.ingest([{ id: 'ticket-1', text: '...', cnsArtId: '...' }]);   // embeds only what consent allows
//   await guard.onWebhook(verifyWebhook({ headers, rawBody, secrets }));      // purges when consent ends
//   await guard.reconcile();                                                  // catches anything a webhook missed

import { createHash, createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

const sha256Hex = (s) => createHash('sha256').update(s ?? '').digest('hex');

/** The CM1-HMAC-SHA256 headers for one signed request (ADR-041). */
export function signRequest(secret, method, path, query, body, now = Date.now()) {
  const ts = Math.floor(now / 1000).toString();
  const nonce = randomBytes(16).toString('hex');
  const text = ['CM1-HMAC-SHA256', ts, nonce, method.toUpperCase(), path, query ?? '', sha256Hex(body)].join('\n');
  return { 'X-Signature-Timestamp': ts, 'X-Signature-Nonce': nonce, 'X-Signature': createHmac('sha256', secret).update(text).digest('hex') };
}

/** What a check says. ALLOW means use the data; DENY means do not; UNAVAILABLE means it could not be checked, which is not a no. */
export const Decision = Object.freeze({ ALLOW: 'ALLOW', DENY: 'DENY', UNAVAILABLE: 'UNAVAILABLE' });

/**
 * Asks the Consent Manager whether a consent allows a purpose right now. A consent token lives minutes (ADR-027): the client
 * fetches a fresh one and keeps it only until shortly before it expires. A 503 or a network failure is `UNAVAILABLE`, never
 * `DENY`: the platform could not decide, so the caller must neither use the data nor treat the consent as withdrawn.
 */
export class ConsentClient {
  constructor({ gateway, apiKey, signingSecret, fetchImpl = fetch, now = () => Date.now() }) {
    if (!gateway || !apiKey || !signingSecret) throw new Error('gateway, apiKey and signingSecret are needed');
    Object.assign(this, { gateway: gateway.replace(/\/+$/, ''), apiKey, signingSecret, fetchImpl, now });
    this.tokens = new Map();
  }

  async #send(method, pathAndQuery, headers = {}) {
    const url = new URL(pathAndQuery, `${this.gateway}/`);
    const signed = signRequest(this.signingSecret, method, url.pathname, url.search.slice(1), '', this.now());
    return this.fetchImpl(url, { method, headers: { 'X-API-Key': this.apiKey, ...signed, ...headers } });
  }

  /** A consent token for the consent, or null when none can be had (withdrawn or not found). Throws on a refusal that is ours to fix. */
  async token(cnsArtId) {
    const cached = this.tokens.get(cnsArtId);
    if (cached && cached.until > this.now()) return cached.cnsTkn;
    const res = await this.#send('POST', `/api/cns-cmd/cns-arts/${encodeURIComponent(cnsArtId)}/token`);
    if (res.status === 404 || res.status === 409) return null;
    if (res.status === 401 || res.status === 403) throw new Error(`the Consent Manager refused this key (${res.status})`);
    if (!res.ok) {
      const e = new Error(`token request answered ${res.status}`);
      e.unavailable = true;
      throw e;
    }
    const body = await res.json();
    this.tokens.set(cnsArtId, { cnsTkn: body.cnsTkn, until: Math.min(Date.parse(body.exp) - 30_000, this.now() + 240_000) });
    return body.cnsTkn;
  }

  /** @returns {Promise<{decision: string, reason?: string, retryAfterSeconds?: number}>} */
  async verify(cnsArtId, purpose) {
    try {
      const tkn = await this.token(cnsArtId);
      if (tkn === null) return { decision: Decision.DENY, reason: 'consent-not-in-force' };
      const res = await this.#send('GET', `/api/cns-qry/cns-arts/${encodeURIComponent(cnsArtId)}/verify?purpose=${encodeURIComponent(purpose)}`, { 'X-Cns-Tkn': tkn });
      if (res.status === 200) return { decision: Decision.ALLOW };
      if (res.status === 403) {
        this.tokens.delete(cnsArtId);
        const problem = await res.json().catch(() => ({}));
        return { decision: Decision.DENY, reason: problem.code ?? 'not-allowed' };
      }
      if (res.status === 401) throw new Error('the Consent Manager refused this key (401)');
      return { decision: Decision.UNAVAILABLE, reason: 'consent-check-unavailable', retryAfterSeconds: Number(res.headers.get('retry-after')) || 5 };
    } catch (e) {
      if (e.unavailable || e.name === 'TypeError' || e.name === 'TimeoutError') return { decision: Decision.UNAVAILABLE, reason: 'consent-check-unavailable', retryAfterSeconds: 5 };
      throw e;
    }
  }
}

/**
 * Embeds only what a consent allows, and takes it back out when the consent ends.
 *
 * @param {object} opts
 * @param {ConsentClient|{verify: Function}} opts.client
 * @param {object} opts.store  upsert(vector), deleteByConsent(cnsArtId), consentIds(); optionally deleteByConsentAndCategory
 * @param {Function} opts.embed  async (text) => number[]  : called only for an item whose consent allows the purpose
 * @param {string} opts.purpose  the purpose code the pipeline works under
 * @param {number} [opts.recheckAfterMs]  embedding can be slow: the consent is checked again before storing when it took longer (default 5000)
 */
export class IngestionGuard {
  constructor({ client, store, embed, purpose, recheckAfterMs = 5000, now = () => Date.now() }) {
    Object.assign(this, { client, store, embed, purpose, recheckAfterMs, now });
  }

  /**
   * @param {{id: string, text: string, cnsArtId?: string, categories?: string[]}[]} items
   * @returns {Promise<{ingested: string[], skipped: {id: string, reason: string}[], deferred: {id: string, retryAfterSeconds: number}[]}>}
   *   `skipped` will not be retried (no consent, or it does not allow this purpose); `deferred` could not be checked and should be.
   */
  async ingest(items) {
    const out = { ingested: [], skipped: [], deferred: [] };
    for (const item of items) {
      if (!item.cnsArtId) {
        out.skipped.push({ id: item.id, reason: 'no-consent-reference' });
        continue;
      }
      const gate = await this.client.verify(item.cnsArtId, this.purpose);
      if (gate.decision === Decision.DENY) {
        out.skipped.push({ id: item.id, reason: gate.reason });
        continue;
      }
      if (gate.decision === Decision.UNAVAILABLE) {
        out.deferred.push({ id: item.id, retryAfterSeconds: gate.retryAfterSeconds ?? 5 });
        continue;
      }
      const started = this.now();
      const vector = await this.embed(item.text);
      if (this.now() - started > this.recheckAfterMs) {
        // the person may have withdrawn while the embedding was computed
        const again = await this.client.verify(item.cnsArtId, this.purpose);
        if (again.decision === Decision.DENY) {
          out.skipped.push({ id: item.id, reason: again.reason });
          continue;
        }
        if (again.decision === Decision.UNAVAILABLE) {
          out.deferred.push({ id: item.id, retryAfterSeconds: again.retryAfterSeconds ?? 5 });
          continue;
        }
      }
      await this.store.upsert({ id: item.id, vector, metadata: { cnsArtId: item.cnsArtId, purpose: this.purpose, categories: item.categories ?? [], ingestedAt: new Date(this.now()).toISOString() } });
      out.ingested.push(item.id);
    }
    return out;
  }

  /**
   * Acts on a verified webhook event (see verifyWebhook): a withdrawn or lapsed consent takes its vectors out; a withdrawn data
   * category takes out the vectors of that category (or of the whole consent, when the store cannot tell). Anything else changes nothing.
   */
  async onWebhook(event) {
    const cnsArtId = event?.data?.cnsArtId;
    if (!cnsArtId) return { action: 'ignored', deleted: 0 };
    switch (event.type) {
      case 'consent.withdrawn':
      case 'consent.lapsed':
        return { action: 'purged', deleted: await this.store.deleteByConsent(cnsArtId) };
      case 'consent.modified':
        if (event.data.change === 'CATEGORY_WITHDRAWN' && event.data.dCatWithdrawn) {
          return typeof this.store.deleteByConsentAndCategory === 'function'
            ? { action: 'purged-category', deleted: await this.store.deleteByConsentAndCategory(cnsArtId, event.data.dCatWithdrawn) }
            : { action: 'purged', deleted: await this.store.deleteByConsent(cnsArtId) };
        }
        return { action: 'ignored', deleted: 0 };
      default:
        return { action: 'ignored', deleted: 0 };
    }
  }

  /**
   * Checks every consent the store holds vectors for, and takes out those no longer allowed. This is what catches a webhook that
   * never arrived. A consent that cannot be checked right now is left alone: "could not check" is not "withdrawn".
   */
  async reconcile() {
    const summary = { checked: 0, purged: 0, deleted: 0, unavailable: 0 };
    for (const cnsArtId of await this.store.consentIds()) {
      summary.checked++;
      const d = await this.client.verify(cnsArtId, this.purpose);
      if (d.decision === Decision.DENY) {
        summary.purged++;
        summary.deleted += await this.store.deleteByConsent(cnsArtId);
      } else if (d.decision === Decision.UNAVAILABLE) {
        summary.unavailable++;
      }
    }
    return summary;
  }
}

/**
 * Verifies a webhook as Standard Webhooks does (docs/api/df-webhooks.asyncapi.yaml): HMAC-SHA256 over
 * `webhook-id.webhook-timestamp.body`, keyed by the base64 secret after `whsec_`, compared in constant time with each `v1,`
 * signature (two while a secret is rotating); a timestamp more than 5 minutes off is refused. Returns the parsed event, with
 * `webhookId` to de-duplicate on. Throws on anything that does not verify.
 *
 * @param {{headers: Record<string, string>, rawBody: string|Buffer, secrets: string[], toleranceSeconds?: number, now?: number}} p
 */
export function verifyWebhook({ headers, rawBody, secrets, toleranceSeconds = 300, now = Date.now() }) {
  const h = Object.fromEntries(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), v]));
  const id = h['webhook-id'];
  const ts = h['webhook-timestamp'];
  const sigs = (h['webhook-signature'] ?? '').split(' ').filter((s) => s.startsWith('v1,')).map((s) => s.slice(3));
  if (!id || !ts || sigs.length === 0 || !/^\d+$/.test(ts)) throw new Error('not a signed webhook');
  if (Math.abs(now / 1000 - Number(ts)) > toleranceSeconds) throw new Error('webhook timestamp is too far from now');
  const body = Buffer.isBuffer(rawBody) ? rawBody.toString('utf8') : rawBody;
  const ok = secrets.some((secret) => {
    const key = Buffer.from(secret.replace(/^whsec_/, ''), 'base64');
    const expected = createHmac('sha256', key).update(`${id}.${ts}.${body}`).digest();
    return sigs.some((s) => {
      const given = Buffer.from(s, 'base64');
      return given.length === expected.length && timingSafeEqual(given, expected);
    });
  });
  if (!ok) throw new Error('webhook signature does not verify');
  return { ...JSON.parse(body), webhookId: id };
}

/** Remembers the webhook ids it has seen (delivery is at least once), forgetting the oldest past `max`. */
export class SeenSet {
  constructor(max = 10_000) {
    this.max = max;
    this.ids = new Set();
  }

  /** True the first time an id is offered, false after. */
  first(id) {
    if (this.ids.has(id)) return false;
    this.ids.add(id);
    if (this.ids.size > this.max) this.ids.delete(this.ids.values().next().value);
    return true;
  }
}

/** A vector store in memory, to show the interface a real one (pgvector, Qdrant, Pinecone ...) implements, and for tests. */
export class InMemoryVectorStore {
  constructor() {
    this.items = new Map();
  }

  async upsert(v) {
    this.items.set(v.id, v);
  }

  async deleteByConsent(cnsArtId) {
    let n = 0;
    for (const [id, v] of this.items) {
      if (v.metadata.cnsArtId === cnsArtId) {
        this.items.delete(id);
        n++;
      }
    }
    return n;
  }

  async deleteByConsentAndCategory(cnsArtId, category) {
    let n = 0;
    for (const [id, v] of this.items) {
      if (v.metadata.cnsArtId === cnsArtId && v.metadata.categories.includes(category)) {
        this.items.delete(id);
        n++;
      }
    }
    return n;
  }

  async consentIds() {
    return [...new Set([...this.items.values()].map((v) => v.metadata.cnsArtId))];
  }
}
