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

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Platform } from "react-native";
import * as AuthSession from "expo-auth-session";
import * as SecureStore from "expo-secure-store";
import * as WebBrowser from "expo-web-browser";
import { friendlyError } from "api-client";
import { ISSUER, KC_CLIENT_ID, SCHEME } from "./config";
import { needsRefresh, personOf, tokensFrom, type Person, type Tokens } from "./session";

WebBrowser.maybeCompleteAuthSession();

const REFRESH_KEY = "cm.dp.refresh";
const PKCE_KEY = "cm.dp.pkce";

/**
 * On web, sign-in leaves the page and comes back to /auth (a popup is blocked by many mobile browsers), so the PKCE
 * verifier and state wait in session storage, and the return's URL is read before the router moves on from it.
 */
const returnedTo: URL | null =
  Platform.OS === "web" && typeof window !== "undefined" && new URL(window.location.href).searchParams.has("code")
    ? new URL(window.location.href)
    : null;

function takePkce(): { verifier: string; state: string } | null {
  try {
    const saved = sessionStorage.getItem(PKCE_KEY);
    sessionStorage.removeItem(PKCE_KEY);
    return saved ? JSON.parse(saved) : null;
  } catch {
    return null;
  }
}

/**
 * The refresh token lives in the Keychain or Android Keystore (ADR-032 §4), on this device only and never in a
 * backup. The web preview keeps it in memory: a reload signs in again, silently while Keycloak's session lasts.
 */
const store = {
  get: () => (Platform.OS === "web" ? Promise.resolve(null) : SecureStore.getItemAsync(REFRESH_KEY)),
  set: (value: string) =>
    Platform.OS === "web"
      ? Promise.resolve()
      : SecureStore.setItemAsync(REFRESH_KEY, value, {
          keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
        }),
  clear: () => (Platform.OS === "web" ? Promise.resolve() : SecureStore.deleteItemAsync(REFRESH_KEY)),
};

export interface Auth {
  /** Loading the stored session; then signed in or not. */
  state: "starting" | "signed-out" | "signed-in";
  person: Person | null;
  error: string | null;
  /** Opens Keycloak's sign-in (a code by email or text, ADR-035) in the system browser. */
  signIn: () => Promise<void>;
  signOut: () => Promise<void>;
  /** An access token with at least 30 seconds left, refreshed if need be; signs out if the session has ended. */
  token: () => Promise<string>;
}

const AuthContext = createContext<Auth | null>(null);

export function useAuth(): Auth {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error("useAuth outside AuthProvider");
  return auth;
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const discovery = AuthSession.useAutoDiscovery(ISSUER);
  const redirectUri = useMemo(() => AuthSession.makeRedirectUri({ scheme: SCHEME, path: "auth" }), []);
  const [request, , promptAsync] = AuthSession.useAuthRequest(
    { clientId: KC_CLIENT_ID, redirectUri, scopes: ["openid", "profile", "email"], usePKCE: true },
    discovery,
  );
  const [state, setState] = useState<Auth["state"]>("starting");
  const [error, setError] = useState<string | null>(null);
  const tokens = useRef<Tokens | null>(null);
  const [person, setPerson] = useState<Person | null>(null);
  const refreshing = useRef<Promise<Tokens> | null>(null);

  const accept = useCallback(async (t: Tokens) => {
    tokens.current = t;
    if (t.refreshToken) await store.set(t.refreshToken);
    setPerson(personOf(t.accessToken));
    setState("signed-in");
  }, []);

  const forget = useCallback(async () => {
    tokens.current = null;
    setPerson(null);
    await store.clear().catch(() => undefined);
    setState("signed-out");
  }, []);

  /** One refresh at a time: screens asking together share it, so a refresh token is never spent twice. */
  const refresh = useCallback(
    (refreshToken: string): Promise<Tokens> => {
      if (!discovery) return Promise.reject(new Error("Sign-in isn't reachable yet"));
      refreshing.current ??= AuthSession.refreshAsync({ clientId: KC_CLIENT_ID, refreshToken }, discovery)
        .then((r) => tokensFrom({ ...r, refreshToken: r.refreshToken ?? refreshToken }))
        .finally(() => {
          refreshing.current = null;
        });
      return refreshing.current;
    },
    [discovery],
  );

  // a stored session resumes without a new sign-in, while Keycloak still honours it; on web, a sign-in may be returning
  useEffect(() => {
    if (!discovery || state !== "starting") return;
    if (returnedTo) {
      const pkce = takePkce();
      const code = returnedTo.searchParams.get("code");
      window.history.replaceState(null, "", "/");
      if (!pkce || !code || pkce.state !== returnedTo.searchParams.get("state")) {
        setError("That sign-in couldn't be finished. Try again.");
        setState("signed-out");
        return;
      }
      AuthSession.exchangeCodeAsync(
        { clientId: KC_CLIENT_ID, code, redirectUri, extraParams: { code_verifier: pkce.verifier } },
        discovery,
      )
        .then((r) => accept(tokensFrom(r)))
        .catch((err) => {
          setError(friendlyError(err));
          setState("signed-out");
        });
      return;
    }
    store
      .get()
      .then(async (stored) => {
        if (!stored) return setState("signed-out");
        await accept(await refresh(stored));
      })
      .catch(() => forget());
  }, [discovery, state, redirectUri, accept, refresh, forget]);

  const signIn = useCallback(async () => {
    if (!request || !discovery) return;
    setError(null);
    if (Platform.OS === "web" && request.url && request.codeVerifier && request.state) {
      sessionStorage.setItem(PKCE_KEY, JSON.stringify({ verifier: request.codeVerifier, state: request.state }));
      window.location.assign(request.url);
      return;
    }
    const result = await promptAsync();
    if (result.type !== "success") {
      if (result.type === "error") setError(result.error?.message ?? "Sign-in didn't finish");
      return;
    }
    try {
      const r = await AuthSession.exchangeCodeAsync(
        {
          clientId: KC_CLIENT_ID,
          code: result.params.code,
          redirectUri,
          extraParams: request.codeVerifier ? { code_verifier: request.codeVerifier } : undefined,
        },
        discovery,
      );
      await accept(tokensFrom(r));
    } catch (err) {
      setError(friendlyError(err));
    }
  }, [request, discovery, promptAsync, redirectUri, accept]);

  const signOut = useCallback(async () => {
    const t = tokens.current;
    await forget();
    if (!discovery) return;
    // end Keycloak's session too, so the next person on this phone signs in as themselves
    if (t?.refreshToken && discovery.revocationEndpoint) {
      await AuthSession.revokeAsync({ token: t.refreshToken, clientId: KC_CLIENT_ID }, discovery).catch(() => false);
    }
    if (discovery.endSessionEndpoint) {
      const url =
        `${discovery.endSessionEndpoint}?client_id=${encodeURIComponent(KC_CLIENT_ID)}` +
        `&post_logout_redirect_uri=${encodeURIComponent(redirectUri)}` +
        (t?.idToken ? `&id_token_hint=${encodeURIComponent(t.idToken)}` : "");
      await WebBrowser.openAuthSessionAsync(url, redirectUri).catch(() => undefined);
    }
  }, [discovery, redirectUri, forget]);

  const token = useCallback(async () => {
    const t = tokens.current;
    if (!t) throw new Error("Not signed in");
    if (!needsRefresh(t)) return t.accessToken;
    if (!t.refreshToken) {
      await forget();
      throw new Error("Your session has ended. Sign in again.");
    }
    try {
      const fresh = await refresh(t.refreshToken);
      await accept(fresh);
      return fresh.accessToken;
    } catch {
      await forget();
      throw new Error("Your session has ended. Sign in again.");
    }
  }, [refresh, accept, forget]);

  const value = useMemo<Auth>(
    () => ({ state: discovery || state !== "starting" ? state : "starting", person, error, signIn, signOut, token }),
    [discovery, state, person, error, signIn, signOut, token],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
