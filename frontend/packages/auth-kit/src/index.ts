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

export function getKeycloak(): Keycloak | null {
  return kcInstance;
}

export async function initKeycloak(opts: KeycloakConnectOpts): Promise<Keycloak> {
  if (kcInstance) return kcInstance;
  const kc = new Keycloak({ url: opts.url, realm: opts.realm, clientId: opts.clientId });
  await kc.init({ pkceMethod: "S256", onLoad: opts.onLoad });
  kcInstance = kc;
  return kc;
}

export function toAuthInfo(kc: Keycloak): AuthInfo | null {
  if (!kc.authenticated || !kc.token) return null;
  const parsed = (kc.tokenParsed ?? {}) as Record<string, unknown>;
  const roles = Array.isArray(parsed.roles) ? (parsed.roles as string[]) : [];
  return {
    token: kc.token,
    subject: kc.subject ?? "",
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
