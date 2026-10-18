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
import { stf, type StfAcct } from "api-client";
import { Badge, Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { freshToken, type Session } from "./session";

/** The people who run the platform. Administrators add operators and switch accounts off and on. */
export default function Staff({ session }: { session: Session }) {
  const [staff, setStaff] = useState<StfAcct[] | null>(null);
  const [form, setForm] = useState({ email: "", name: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const load = async () => {
    try {
      setStaff(await stf.list(session.gwBase, await freshToken(session.auth)));
    } catch (err) {
      setError(friendlyError(err));
      setStaff((prev) => prev ?? []);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const run = async (action: (token: string) => Promise<unknown>, message: string) => {
    setBusy(true);
    setError(null);
    setDone(null);
    try {
      await action(await freshToken(session.auth));
      setDone(message);
      await load();
      return true;
    } catch (err) {
      setError(friendlyError(err));
      return false;
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Platform staff"
        subtitle="Administrators manage plans, suspensions and staff; operators view and onboard tenants."
      />
      {error ? <ErrorBanner message={error} /> : null}
      {done ? <p className="adm-ok" role="status">{done}</p> : null}

      {session.isAdmin ? (
        <Card style={{ marginBottom: 18 }}>
          <h3 style={{ marginTop: 0 }}>Add an operator</h3>
          <p className="adm-note">They sign in with a one-time code sent to their email address; there is no password.</p>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              const email = form.email.trim();
              if (await run((token) => stf.createOperator(session.gwBase, token, { email, name: form.name.trim() }), `${email} can now sign in as an operator.`)) {
                setForm({ email: "", name: "" });
              }
            }}
          >
            <div className="adm-form-grid">
              <Field label="Email">
                <input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </Field>
              <Field label="Name">
                <input required maxLength={100} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </Field>
            </div>
            <Button type="submit" variant="primary" disabled={busy}>
              Add operator
            </Button>
          </form>
        </Card>
      ) : null}

      {staff === null ? (
        <Spinner />
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                <th>Status</th>
                <th>Since</th>
                {session.isAdmin ? <th aria-label="Actions" /> : null}
              </tr>
            </thead>
            <tbody>
              {staff.map((s) => (
                <tr key={s.id}>
                  <td>
                    {s.name}
                    {s.id === session.auth.subject ? " (you)" : ""}
                  </td>
                  <td>{s.email}</td>
                  <td>{s.role === "CM_ADMIN" ? "Administrator" : "Operator"}</td>
                  <td>
                    <Badge status={s.enabled ? "ACTIVE" : "DISABLED"} />
                  </td>
                  <td>{s.createdAt ? new Date(s.createdAt).toLocaleDateString() : "—"}</td>
                  {session.isAdmin ? (
                    <td>
                      {s.id !== session.auth.subject ? (
                        <Button
                          variant={s.enabled ? "danger" : undefined}
                          disabled={busy}
                          onClick={() => {
                            if (s.enabled && !window.confirm(`Switch off ${s.email}? They are signed out everywhere.`)) return;
                            run(
                              (token) => stf.setEnabled(session.gwBase, token, s.id, !s.enabled),
                              s.enabled ? `${s.email} was switched off and signed out.` : `${s.email} can sign in again.`,
                            );
                          }}
                        >
                          {s.enabled ? "Switch off" : "Switch on"}
                        </Button>
                      ) : null}
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
