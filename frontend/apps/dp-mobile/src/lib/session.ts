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

/** Who is signed in, read from the access token's claims. */
export interface Person {
  subject: string;
  /** What they signed in with: their email address, or a mobile number for a phone-only account. */
  username?: string;
  email?: string;
  roles: string[];
}

/** A session's tokens. Only the refresh token outlives the app, in the platform's secure storage. */
export interface Tokens {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  /** When the access token expires, in epoch milliseconds. */
  expiresAt: number;
}

/** Refresh this long before the access token expires, so a request never carries one that lapses in flight. */
export const REFRESH_MARGIN_MS = 30_000;

export const needsRefresh = (t: Tokens, now: number = Date.now()) => t.expiresAt - now < REFRESH_MARGIN_MS;

/** The tokens a token response gives: expiry from its lifetime, or from the token's own exp claim. */
export function tokensFrom(
  r: { accessToken: string; refreshToken?: string; idToken?: string; expiresIn?: number; issuedAt?: number },
  now: number = Date.now(),
): Tokens {
  const claims = claimsOf(r.accessToken);
  const fromExp = typeof claims?.exp === "number" ? claims.exp * 1000 : null;
  const issued = r.issuedAt ? r.issuedAt * 1000 : now;
  return {
    accessToken: r.accessToken,
    refreshToken: r.refreshToken,
    idToken: r.idToken,
    expiresAt: r.expiresIn ? issued + r.expiresIn * 1000 : (fromExp ?? now + 60_000),
  };
}

/** A JWT's payload, unverified: the app only reads its own token's claims; the gateway verifies every call. */
export function claimsOf(jwt: string): Record<string, unknown> | null {
  const part = jwt.split(".")[1];
  if (!part) return null;
  try {
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

export function personOf(accessToken: string): Person | null {
  const c = claimsOf(accessToken);
  if (!c || typeof c.sub !== "string") return null;
  return {
    subject: c.sub,
    username: typeof c.preferred_username === "string" ? c.preferred_username : undefined,
    email: typeof c.email === "string" ? c.email : undefined,
    roles: Array.isArray(c.roles) ? (c.roles as string[]) : [],
  };
}

/** Only principals use this app; an account from another audience is told so rather than shown empty lists. */
export const isPrincipal = (p: Person) => p.roles.includes("DP");
