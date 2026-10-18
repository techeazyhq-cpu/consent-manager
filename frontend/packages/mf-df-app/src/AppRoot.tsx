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
import { ErrorBanner, Spinner, WrongAccountNotice } from "ui-kit/src/index";
import { memberRoleOf } from "./roles";
import DfConsoleApp from "./App";

const KC_BASE = import.meta.env.VITE_KC_BASE_URL ?? "http://localhost:8080";
const KC_REALM = import.meta.env.VITE_KC_REALM ?? "cm-df";
const KC_CLIENT = import.meta.env.VITE_KC_CLIENT_ID ?? "cm-df-app";
const GW_BASE = import.meta.env.VITE_GW_BASE_URL ?? "http://localhost:9000";

/**
 * The DF Console: a fiduciary's people sign in as themselves with a one-time code (ADR-033), and what they see
 * follows their role there. Anyone else (platform staff, a data principal, a member removed meanwhile) is told
 * they're signed in with the wrong account.
 */
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

  const role = auth ? memberRoleOf(auth.roles) : undefined;
  if (!auth || !auth.dfId || !role) {
    return (
      <div className="cm-page">
        <WrongAccountNotice
          appName="DF Console"
          requiredAccount="an account at a data fiduciary"
          username={auth?.username}
          onSignOut={() => (auth ? auth.logout() : window.location.reload())}
        />
      </div>
    );
  }

  return <DfConsoleApp auth={auth} dfId={auth.dfId} role={role} gwBase={GW_BASE} />;
}
