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
import { aiDraftingConfig, dfReg, type AiAccessListDto } from "api-client";
import { Button, Card, ErrorBanner, Spinner, friendlyError } from "ui-kit/src/index";
import { tenantHref } from "./routes";
import { freshToken, type Session } from "./session";

const fmt = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString() : "");

/**
 * Which organisations may draft purposes with the model (ADR-114). Staff turn it on when onboarding a tenant, here for one already onboarded, or by
 * approving its request; administrators also turn it off and decline a request, giving the reason the organisation will see.
 */
export default function AiDraftingAccess({ session }: { session: Session }) {
  const [list, setList] = useState<AiAccessListDto | null>(null);
  const [names, setNames] = useState<Record<string, string>>({});
  const [tenants, setTenants] = useState<{ id: string; name: string }[]>([]);
  const [pick, setPick] = useState("");
  const [declining, setDeclining] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const token = await freshToken(session.auth);
      setList(await aiDraftingConfig.accessList(session.gwBase, token));
    } catch (err) {
      setError(friendlyError(err));
    }
  }, [session.auth, session.gwBase]);

  useEffect(() => {
    load();
    // names are a convenience: without them the ids still say which organisation
    freshToken(session.auth)
      .then((t) => dfReg.list(session.gwBase, t))
      .then((rows) => {
        setNames(Object.fromEntries(rows.map((r) => [r.id, r.name])));
        setTenants(rows.map((r) => ({ id: r.id, name: r.name })).sort((a, b) => a.name.localeCompare(b.name)));
      })
      .catch(() => {});
  }, [load, session.auth, session.gwBase]);

  const run = async (action: (token: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action(await freshToken(session.auth));
      setDeclining(null);
      setReason("");
      setPick("");
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  if (!list) return error ? <ErrorBanner message={error} /> : <Spinner />;
  const nameOf = (id: string) => names[id] ?? id.slice(0, 8);
  const link = (id: string) => <a href={tenantHref(id)}>{nameOf(id)}</a>;
  const onIds = new Set(list.enabled.map((r) => r.dfId));
  const candidates = tenants.filter((t) => !onIds.has(t.id));

  return (
    <>
      <h3 className="cm-section-title">Organisations that can draft</h3>
      <p className="cm-note">
        Drafting is off for an organisation until you turn it on, when onboarding it or here, or approve its request from the DF Console.
      </p>
      {error ? <ErrorBanner message={error} /> : null}

      <Card style={{ marginBottom: 16 }}>
        <strong>Requests waiting</strong>
        {list.pending.length === 0 ? (
          <p className="cm-note">None.</p>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table className="cm-table">
              <thead>
                <tr>
                  <th>Organisation</th>
                  <th>Asked</th>
                  <th>Note</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.pending.map((r) => (
                  <tr key={r.dfId}>
                    <td>{link(r.dfId)}</td>
                    <td>{fmt(r.requestedAt)}</td>
                    <td style={{ whiteSpace: "pre-wrap" }}>{r.note ?? "—"}</td>
                    <td>
                      {session.isAdmin ? (
                        declining === r.dfId ? (
                          <form
                            onSubmit={(e) => {
                              e.preventDefault();
                              run((t) => aiDraftingConfig.declineAccess(session.gwBase, t, r.dfId, reason.trim()));
                            }}
                            style={{ display: "flex", gap: 8, flexWrap: "wrap" }}
                          >
                            <input
                              aria-label={`Why ${nameOf(r.dfId)} is turned down`}
                              placeholder="Why (the organisation sees this)"
                              maxLength={500}
                              value={reason}
                              onChange={(e) => setReason(e.target.value)}
                            />
                            <Button type="submit" variant="danger" disabled={busy || !reason.trim()}>
                              Decline
                            </Button>
                            <Button type="button" disabled={busy} onClick={() => setDeclining(null)}>
                              Cancel
                            </Button>
                          </form>
                        ) : (
                          <span style={{ display: "flex", gap: 8 }}>
                            <Button variant="primary" disabled={busy} onClick={() => run((t) => aiDraftingConfig.approveAccess(session.gwBase, t, r.dfId))}>
                              Approve
                            </Button>
                            <Button disabled={busy} onClick={() => setDeclining(r.dfId)}>
                              Decline…
                            </Button>
                          </span>
                        )
                      ) : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {!session.isAdmin && list.pending.length > 0 ? <p className="cm-note">Only a platform administrator decides a request.</p> : null}
      </Card>

      <Card style={{ marginBottom: 16 }}>
        <strong>Turned on</strong>
        {list.enabled.length === 0 ? (
          <p className="cm-note">No organisation can draft yet.</p>
        ) : (
          <ul style={{ listStyle: "none", padding: 0 }}>
            {list.enabled.map((r) => (
              <li key={r.dfId} style={{ display: "flex", gap: 12, alignItems: "center", padding: "4px 0" }}>
                <span>{link(r.dfId)}</span>
                <span className="cm-note" style={{ margin: 0 }}>
                  since {fmt(r.updatedAt)}
                </span>
                {session.isAdmin ? (
                  <Button disabled={busy} onClick={() => run((t) => aiDraftingConfig.setAccess(session.gwBase, t, r.dfId, false))}>
                    Turn off for {nameOf(r.dfId)}
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (pick) run((t) => aiDraftingConfig.setAccess(session.gwBase, t, pick, true));
          }}
          style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", marginTop: 8 }}
        >
          <select aria-label="Turn on for an organisation" value={pick} onChange={(e) => setPick(e.target.value)}>
            <option value="">Turn on for an organisation…</option>
            {candidates.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          <Button type="submit" disabled={busy || !pick}>
            Turn on
          </Button>
        </form>
      </Card>
    </>
  );
}
