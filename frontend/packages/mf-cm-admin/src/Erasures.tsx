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
import { ERASURE_STEP_LABELS, dsr, type DpErasureDto, type DpErasureSts } from "api-client";
import { Button, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { freshToken, type Session } from "./session";

const FILTERS: { key: DpErasureSts | "ALL"; label: string }[] = [
  { key: "STUCK", label: "Stuck" },
  { key: "RUNNING", label: "Running" },
  { key: "DONE", label: "Done" },
  { key: "ALL", label: "All" },
];

const fmt = (iso: string | null) => (iso ? new Date(iso).toLocaleString() : "—");

/**
 * Principals closing their accounts (ADR-059): each erasure's step, and why one is stuck. A stuck erasure has been
 * logged as an error; once its cause is fixed (a service or Keycloak down), an administrator restarts its step here.
 * Principals appear by their pseudonymous id only.
 */
export default function Erasures({ session }: { session: Session }) {
  const [filter, setFilter] = useState<DpErasureSts | "ALL">("STUCK");
  const [rows, setRows] = useState<DpErasureDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setRows(await dsr.erasures.list(session.gwBase, await freshToken(session.auth), filter === "ALL" ? undefined : filter));
    } catch (err) {
      setError(friendlyError(err));
      setRows([]);
    }
  }, [session.auth, session.gwBase, filter]);

  useEffect(() => {
    setRows(null);
    load();
  }, [load]);

  const retry = async (id: string) => {
    setBusy(id);
    setError(null);
    try {
      await dsr.erasures.retry(session.gwBase, await freshToken(session.auth), id);
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(null);
    }
  };

  return (
    <>
      <PageHeader
        title="Account erasures"
        subtitle="Data principals closing their accounts. A stuck one waits for its cause to be fixed, then a retry."
      />
      <div className="cm-chips" role="group" aria-label="Show erasures">
        {FILTERS.map((f) => (
          <button key={f.key} aria-pressed={filter === f.key} onClick={() => setFilter(f.key)}>
            {f.label}
          </button>
        ))}
      </div>
      {error ? <ErrorBanner message={error} /> : null}
      {rows === null ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <p className="cm-note">{filter === "STUCK" ? "Nothing is stuck." : "No erasures to show."}</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Principal</th>
                <th>Asked</th>
                <th>Status</th>
                <th>Step</th>
                <th>Consents withdrawn</th>
                <th>Finished</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>
                    <code>{r.dpId.slice(0, 8)}</code>
                  </td>
                  <td>{fmt(r.requestedAt)}</td>
                  <td>
                    <span className={`cm-badge cm-badge-${r.sts === "STUCK" ? "failed" : r.sts === "DONE" ? "delivered" : "pending"}`}>
                      {r.sts}
                    </span>
                  </td>
                  <td>
                    {r.step ? ERASURE_STEP_LABELS[r.step] : "—"}
                    {r.sts !== "DONE" && r.attempts ? <div className="cm-note" style={{ margin: 0 }}>Attempt {r.attempts}</div> : null}
                    {r.lastError ? <div className="cm-note" style={{ margin: 0 }}>{r.lastError}</div> : null}
                  </td>
                  <td>{r.consentsWithdrawn}</td>
                  <td>{fmt(r.finishedAt)}</td>
                  <td>
                    {r.sts === "STUCK" && session.isAdmin ? (
                      <Button disabled={busy === r.id} onClick={() => retry(r.id)}>
                        Retry
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
