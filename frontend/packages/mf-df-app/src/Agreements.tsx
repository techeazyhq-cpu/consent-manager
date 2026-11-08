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

import { useCallback, useEffect, useState } from "react";
import { leaving, terms, type LeaveStatusDto, type TermsStatusDto } from "api-client";
import { Badge, Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import type { Console } from "./console";
import { fmtDate } from "./labels";
import { DOCUMENT_LABELS, bindsOrganisation, confirmsName, standingNotice } from "./standing";
import { freshToken } from "./token";

/**
 * The organisation's agreements with the consent manager (ADR-063) and leaving it (ADR-064). Everyone on the team sees
 * where each document and any departure stands; only the Owner accepts a document or asks to leave, since it binds the
 * organisation. A departure can be called off once, before the wind-down ends.
 */
export default function Agreements({ console: c }: { console: Console }) {
  const [docs, setDocs] = useState<TermsStatusDto[] | null>(null);
  const [leave, setLeave] = useState<LeaveStatusDto | null>(null);
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const owner = bindsOrganisation(c.role);
  const canExport = c.role === "OWNER" || c.role === "ADMIN";

  const load = useCallback(async () => {
    try {
      const token = await freshToken(c.auth);
      const [d, l] = await Promise.all([terms.status(c.gwBase, token), leaving.status(c.gwBase, token)]);
      setDocs(d);
      setLeave(l);
    } catch (err) {
      setError(friendlyError(err));
      setDocs((prev) => prev ?? []);
    }
  }, [c.auth, c.gwBase]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action: (token: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action(await freshToken(c.auth));
      setTyped("");
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const notice = standingNotice(leave?.sts, leave?.leavesAt);

  return (
    <>
      <PageHeader
        title="Agreements"
        subtitle="The terms you've accepted from Consent Manager, and what happens if your organisation leaves."
      />
      {error ? <ErrorBanner message={error} /> : null}

      <h3 className="cm-section-title">Terms and data processing agreement</h3>
      {docs === null ? (
        <Spinner />
      ) : (
        <Card>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Document</th>
                <th>Version in force</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {docs.map((d) => (
                <tr key={d.document}>
                  <td>{DOCUMENT_LABELS[d.document] ?? d.document}</td>
                  <td>{d.currentVersion}</td>
                  <td>
                    {d.accepted && d.acceptance ? (
                      <>
                        <Badge status="ACCEPTED" /> <span className="cm-note">{fmtDate(d.acceptance.acceptedAt)}</span>
                      </>
                    ) : (
                      <Badge status="PENDING" />
                    )}
                  </td>
                  <td>
                    {!d.accepted && owner ? (
                      <Button disabled={busy} onClick={() => run((t) => terms.accept(c.gwBase, t, d.document, d.currentVersion))}>
                        Accept
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!owner && docs.some((d) => !d.accepted) ? (
            <p className="cm-note">Only an Owner can accept these on the organisation's behalf.</p>
          ) : null}
          <p className="cm-note">
            An acceptance records who accepted and when. If a document changes, its new version needs accepting again.
          </p>
        </Card>
      )}

      {canExport ? (
        <>
          <h3 className="cm-section-title">Your data</h3>
          <Card>
            <p>
              Everything the platform holds for your organisation, in one JSON file: your profile, team and API key list (never
              the keys or secrets), your purposes with every version, your consent records with their signed receipts, and your
              audit entries. People appear only by pseudonymous id. Large organisations may be cut at 5,000 of a section, and
              the file says so.
            </p>
            <Button
              disabled={busy}
              onClick={() =>
                run(async (token) => {
                  const res = await fetch(`${c.gwBase}/api/df-reg/me/export`, { headers: { Authorization: `Bearer ${token}` } });
                  if (!res.ok) throw Object.assign(new Error("export failed"), { problem: await res.json().catch(() => null), status: res.status });
                  const url = URL.createObjectURL(await res.blob());
                  const a = document.createElement("a");
                  a.href = url;
                  a.download = `consent-manager-export-${new Date().toISOString().slice(0, 10)}.json`;
                  a.click();
                  URL.revokeObjectURL(url);
                })
              }
            >
              {busy ? "Preparing…" : "Download your data"}
            </Button>
          </Card>
        </>
      ) : null}

      <h3 className="cm-section-title">Leaving Consent Manager</h3>
      <Card>
        {leave?.sts === "LEAVING" ? (
          <>
            <p>{notice}</p>
            <dl className="cm-fields">
              <div>
                <dt>Began</dt>
                <dd>{fmtDate(leave.leavingAt)}</dd>
              </div>
              <div>
                <dt>Closes</dt>
                <dd>{fmtDate(leave.leavesAt)}</dd>
              </div>
            </dl>
            {owner ? (
              leave.canCancel ? (
                <>
                  <p className="cm-note">An Owner can call this off once. It returns your organisation to how it was.</p>
                  <Button disabled={busy} onClick={() => run((t) => leaving.cancel(c.gwBase, t))}>
                    Cancel leaving
                  </Button>
                </>
              ) : (
                <p className="cm-note">This departure can't be cancelled: the one cancellation was already used.</p>
              )
            ) : (
              <p className="cm-note">Only an Owner can call this off.</p>
            )}
          </>
        ) : leave?.sts === "CLOSED" || leave?.sts === "SUSPENDED" ? (
          <p>{notice}</p>
        ) : (
          <>
            <p>
              Leaving starts a 30-day wind-down. During it no new requests are accepted and API keys stop working, but your
              records stay readable and you can export them. When it ends:
            </p>
            <ul>
              <li>every consent still in force is ended, and each person gets a receipt;</li>
              <li>your API keys, team accounts, logo and contact details are removed;</li>
              <li>
                the consent records stay, under pseudonymous ids, for seven years because the people they concern are entitled to
                them, and are then deleted.
              </li>
            </ul>
            {owner ? (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  if (confirmsName(typed, c.dfName)) run((t) => leaving.leave(c.gwBase, t, typed.trim()));
                }}
              >
                <Field label={`Type “${c.dfName}” to confirm`}>
                  <input value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
                </Field>
                <Button type="submit" variant="danger" disabled={busy || !confirmsName(typed, c.dfName)}>
                  {busy ? "Starting…" : "Leave Consent Manager"}
                </Button>
              </form>
            ) : (
              <p className="cm-note">Only an Owner can ask for the organisation to leave.</p>
            )}
          </>
        )}
      </Card>
    </>
  );
}
