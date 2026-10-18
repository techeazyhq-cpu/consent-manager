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
import { stf, type StfAcct, type StfSession } from "api-client";
import { Button, Card, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { freshToken, type Session } from "./session";

/**
 * Your own account: who you are, where you're signed in, and signing out everywhere. There is no password to change:
 * staff sign in with a one-time code sent to their email address.
 */
export default function Security({ session }: { session: Session }) {
  const [me, setMe] = useState<StfAcct | null>(null);
  const [sessions, setSessions] = useState<StfSession[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    freshToken(session.auth)
      .then((token) => Promise.all([stf.me(session.gwBase, token), stf.mySessions(session.gwBase, token)]))
      .then(([acct, list]) => {
        setMe(acct);
        setSessions(list);
      })
      .catch((err) => {
        setError(friendlyError(err));
        setSessions([]);
      });
  }, [session]);

  return (
    <>
      <PageHeader title="Sign-in & security" subtitle="You sign in with a one-time code sent to your email address." />
      {error ? <ErrorBanner message={error} /> : null}

      <Card style={{ marginBottom: 18 }}>
        {me ? (
          <dl className="adm-fields">
            <div>
              <dt>Name</dt>
              <dd>{me.name}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>{me.email}</dd>
            </div>
            <div>
              <dt>Role</dt>
              <dd>{me.role === "CM_ADMIN" ? "Administrator" : "Operator"}</dd>
            </div>
            <div>
              <dt>Account since</dt>
              <dd>{me.createdAt ? new Date(me.createdAt).toLocaleDateString() : "—"}</dd>
            </div>
          </dl>
        ) : (
          <Spinner />
        )}
      </Card>

      <h3 className="adm-section-title">Where you're signed in</h3>
      {sessions === null ? (
        <Spinner />
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>From</th>
                <th>Apps</th>
                <th>Signed in</th>
                <th>Last active</th>
              </tr>
            </thead>
            <tbody>
              {sessions.map((s) => (
                <tr key={s.id}>
                  <td>{s.ipAddress}</td>
                  <td>{s.clients.join(", ")}</td>
                  <td>{s.startedAt ? new Date(s.startedAt).toLocaleString() : "—"}</td>
                  <td>{s.lastAccessAt ? new Date(s.lastAccessAt).toLocaleString() : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <div style={{ marginTop: 14 }}>
        <Button
          variant="danger"
          disabled={busy}
          onClick={async () => {
            if (!window.confirm("Sign out on every device, including this one?")) return;
            setBusy(true);
            try {
              await stf.signOutEverywhere(session.gwBase, await freshToken(session.auth));
              session.auth.logout();
            } catch (err) {
              setError(friendlyError(err));
              setBusy(false);
            }
          }}
        >
          Sign out everywhere
        </Button>
      </div>
    </>
  );
}
