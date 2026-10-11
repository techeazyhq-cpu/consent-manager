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

import { useEffect, useState } from "react";
import { initKeycloak, toAuthInfo, type AuthInfo } from "auth-kit";
import { ErrorBanner, Spinner } from "ui-kit/src/index";
import CmAdminApp from "./App";

const KC_BASE = import.meta.env.VITE_KC_BASE_URL ?? "http://localhost:8080";
const KC_REALM = import.meta.env.VITE_KC_REALM ?? "cm-realm";
const KC_CLIENT = import.meta.env.VITE_KC_CLIENT_ID ?? "cm-cm-admin";
const GW_BASE = import.meta.env.VITE_GW_BASE_URL ?? "http://localhost:9000";

/** Standalone entry point for CM Admin (plan §3's CmAdmApp) — independently deployable, no
 * shared shell app. Owns its own Keycloak PKCE session; visiting this app's URL directly
 * forces sign-in immediately (onLoad: 'login-required'). */
export default function AppRoot() {
  const [auth, setAuth] = useState<AuthInfo | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    initKeycloak({ url: KC_BASE, realm: KC_REALM, clientId: KC_CLIENT, onLoad: "login-required" })
      .then((kc) => {
        setAuth(toAuthInfo(kc));
        setReady(true);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : String(err));
        setReady(true);
      });
  }, []);

  if (!ready) {
    return (
      <div className="cm-page">
        <Spinner label="Connecting to Identity Provider…" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="cm-page">
        <ErrorBanner message={`Could not reach Keycloak at ${KC_BASE}: ${error}`} />
      </div>
    );
  }

  if (!auth || !auth.roles.includes("CM_ADMIN")) {
    return (
      <div className="cm-page">
        <ErrorBanner message="Your account does not have the CM_ADMIN role." />
      </div>
    );
  }

  return <CmAdminApp auth={auth} gwBase={GW_BASE} />;
}
