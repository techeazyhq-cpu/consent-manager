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
import { ntf, type AuthInfo, type NtfPreferencesDto } from "api-client";
import { friendlyError } from "ui-kit/src/index";
import { emailNote, withEmail, withPush } from "./prefs";

interface Props {
  auth: AuthInfo;
  gwBase: string;
  onError: (message: string | null) => void;
}

/**
 * How a new consent request reaches the person (ADR-066): push to their devices, which they can pause without removing each
 * one, and an email that says a request waits. A request is always on their dashboard, and sign-in codes always come.
 */
export default function PrefsToggles({ auth, gwBase, onError }: Props) {
  const [prefs, setPrefs] = useState<NtfPreferencesDto | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    ntf.getPreferences(gwBase, auth.token).then(setPrefs).catch((err) => onError(friendlyError(err)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const change = async (body: { push: boolean; email: boolean }) => {
    setBusy(true);
    onError(null);
    try {
      setPrefs(await ntf.setPreferences(gwBase, auth.token, body));
    } catch (err) {
      onError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  if (!prefs) return null;
  return (
    <div style={{ display: "grid", gap: 12, margin: "12px 0" }}>
      <label style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
        <input type="checkbox" checked={prefs.push} disabled={busy} onChange={(e) => change(withPush(prefs, e.target.checked))} />
        <span>
          <strong>Push notifications</strong>
          <br />
          <span className="cm-note">To the browsers and phones you turned notifications on in. Pausing keeps them registered.</span>
        </span>
      </label>
      <label style={{ display: "flex", gap: 8, alignItems: "flex-start" }}>
        <input
          type="checkbox"
          checked={prefs.email}
          disabled={busy || !prefs.canEmail}
          onChange={(e) => change(withEmail(prefs, e.target.checked))}
        />
        <span>
          <strong>Request emails from organisations</strong>
          <br />
          <span className="cm-note">{emailNote(prefs)}</span>
        </span>
      </label>
    </div>
  );
}
