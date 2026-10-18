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
import type { AuthInfo } from "auth-kit";
import { dfTeam, type DfMemberRespDto, type DfRole } from "api-client";
import { Button, Card, Field, ErrorBanner, Spinner, friendlyError } from "ui-kit/src/index";
import { freshToken } from "./token";
import { ROLE_HINTS, ROLE_LABELS, assignableBy } from "./roles";

interface Props {
  auth: AuthInfo;
  role: DfRole;
  gwBase: string;
  canManage: boolean;
}

/**
 * The fiduciary's people (ADR-033). Everyone sees the team; Owners and Administrators invite, change roles and
 * remove, and only an Owner touches ownership. The server enforces all of it; this only hides what would be refused.
 */
export default function Team({ auth, role, gwBase, canManage }: Props) {
  const [members, setMembers] = useState<DfMemberRespDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const assignable = assignableBy(role);
  const [invite, setInvite] = useState<{ email: string; name: string; role: DfRole }>({
    email: "",
    name: "",
    role: "CONSENT_MANAGER",
  });

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setMembers(await dfTeam.list(gwBase, await freshToken(auth)));
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Runs the change, then reloads the team; true if it was made. */
  const act = async (action: (token: string) => Promise<unknown>, done: string): Promise<boolean> => {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      await action(await freshToken(auth));
      setNotice(done);
      await load();
      return true;
    } catch (err) {
      setError(friendlyError(err));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const mayActOn = (member: DfMemberRespDto) =>
    canManage && member.id !== auth.subject && (member.role !== "OWNER" || role === "OWNER");

  return (
    <>
      {error ? <ErrorBanner message={error} /> : null}
      {notice ? (
        <p role="status" style={{ color: "var(--cm-text-dim)" }}>
          {notice}
        </p>
      ) : null}

      {canManage ? (
        <Card className="cm-card">
          <h3 style={{ marginTop: 0 }}>Invite someone</h3>
          <p style={{ marginTop: 0, color: "var(--cm-text-dim)" }}>
            They sign in to this console with a one-time code sent to their email address.
          </p>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "0 16px" }}>
            <Field label="Email address">
              <input
                type="email"
                autoComplete="off"
                value={invite.email}
                onChange={(e) => setInvite({ ...invite, email: e.target.value })}
              />
            </Field>
            <Field label="Name">
              <input value={invite.name} onChange={(e) => setInvite({ ...invite, name: e.target.value })} />
            </Field>
            <Field label="Role">
              <select value={invite.role} onChange={(e) => setInvite({ ...invite, role: e.target.value as DfRole })}>
                {assignable.map((r) => (
                  <option key={r} value={r}>
                    {ROLE_LABELS[r]} — {ROLE_HINTS[r]}
                  </option>
                ))}
              </select>
            </Field>
          </div>
          <Button
            variant="primary"
            disabled={busy || !invite.email.trim() || !invite.name.trim()}
            onClick={() =>
              act(
                (token) => dfTeam.invite(gwBase, token, { ...invite, email: invite.email.trim(), name: invite.name.trim() }),
                `${invite.email.trim()} was invited as ${ROLE_LABELS[invite.role]}.`,
              ).then((made) => made && setInvite({ email: "", name: "", role: "CONSENT_MANAGER" }))
            }
          >
            Invite
          </Button>
        </Card>
      ) : null}

      <div style={{ height: 20 }} />
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <h3 style={{ margin: 0 }}>Team</h3>
        <Button onClick={load}>Refresh</Button>
      </div>

      {loading ? (
        <Spinner />
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Email</th>
                <th>Role</th>
                {canManage ? <th aria-label="Actions" /> : null}
              </tr>
            </thead>
            <tbody>
              {members.map((member) => (
                <tr key={member.id}>
                  <td>
                    {member.name}
                    {member.id === auth.subject ? " (you)" : ""}
                  </td>
                  <td>{member.email}</td>
                  <td>
                    {mayActOn(member) ? (
                      <select
                        aria-label={`Role of ${member.name}`}
                        value={member.role}
                        disabled={busy}
                        onChange={(e) => {
                          const next = e.target.value as DfRole;
                          act(
                            (token) => dfTeam.changeRole(gwBase, token, member.id, next),
                            `${member.name} is now ${ROLE_LABELS[next]}; they'll need to sign in again.`,
                          );
                        }}
                      >
                        {assignable.map((r) => (
                          <option key={r} value={r}>
                            {ROLE_LABELS[r]}
                          </option>
                        ))}
                      </select>
                    ) : (
                      ROLE_LABELS[member.role]
                    )}
                  </td>
                  {canManage ? (
                    <td>
                      {mayActOn(member) ? (
                        <Button
                          disabled={busy}
                          onClick={() => {
                            if (window.confirm(`Remove ${member.name} (${member.email}) from the team? Their account is deleted.`)) {
                              act((token) => dfTeam.remove(gwBase, token, member.id), `${member.name} was removed.`);
                            }
                          }}
                        >
                          Remove
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
