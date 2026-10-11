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

import Keycloak from "keycloak-js";

/**
 * Keycloak PKCE session helper (plan §6/§9a) — used independently by whichever standalone app
 * needs an interactive human login (CM Admin, DP Portal). The DF Console deliberately does NOT
 * use this: a DF's own client-credentials secret (issued at activation, plan §10) is the
 * already-proven auth path for the DF Integration API, so it authenticates itself directly
 * against Keycloak's token endpoint instead of an interactive PKCE session.
 */
export interface AuthInfo {
  token: string;
  subject: string;
  /** What the person signed in with: their email address or, for a phone-only account, their mobile number. */
  username?: string;
  roles: string[];
  dpId?: string;
  email?: string;
  logout: () => void;
}

export interface KeycloakConnectOpts {
  url: string;
  realm: string;
  clientId: string;
  /** 'login-required' forces an immediate redirect for an app with no page of its own to show
   * an unauthenticated visitor; omit for an app that wants to decide that itself. */
  onLoad?: "login-required" | "check-sso";
}

let kcInstance: Keycloak | null = null;
let kcInit: Promise<Keycloak> | null = null;

export function getKeycloak(): Keycloak | null {
  return kcInstance;
}

/**
 * Idempotent while in flight, not just once finished: React StrictMode (every `vite` dev run) mounts effects twice,
 * and two Keycloak instances racing to redeem the same login code made the loser redirect to login again, forever.
 */
export function initKeycloak(opts: KeycloakConnectOpts): Promise<Keycloak> {
  kcInit ??= (async () => {
    const kc = new Keycloak({ url: opts.url, realm: opts.realm, clientId: opts.clientId });
    await kc.init({ pkceMethod: "S256", onLoad: opts.onLoad });
    kcInstance = kc;
    return kc;
  })().catch((err) => {
    kcInit = null;
    throw err;
  });
  return kcInit;
}

export function toAuthInfo(kc: Keycloak): AuthInfo | null {
  if (!kc.authenticated || !kc.token) return null;
  const parsed = (kc.tokenParsed ?? {}) as Record<string, unknown>;
  const roles = Array.isArray(parsed.roles) ? (parsed.roles as string[]) : [];
  return {
    token: kc.token,
    subject: kc.subject ?? "",
    username: typeof parsed.preferred_username === "string" ? parsed.preferred_username : undefined,
    roles,
    dpId: roles.includes("DP") ? kc.subject ?? undefined : undefined,
    email: typeof parsed.email === "string" ? parsed.email : undefined,
    logout: () => kc.logout({ redirectUri: window.location.origin }),
  };
}

/** Refreshes the token if it expires within 30s; forces re-login if the refresh itself fails. */
export async function ensureFreshToken(kc: Keycloak): Promise<void> {
  try {
    await kc.updateToken(30);
  } catch {
    kc.login({ redirectUri: window.location.href });
  }
}
