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
import { anomalies, type AnomalyDto } from "api-client";
import { Button, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { tenantHref } from "./routes";
import { freshToken, type Session } from "./session";

/** What each signal counts, in the words the people reading the finding use. */
export const SIGNAL_LABELS: Record<string, string> = {
  "consent-requests": "Consent requests created",
  "consent-withdrawals": "Consents withdrawn",
  "consent-denials": "Consents denied",
  "api-keys-issued": "API keys issued",
  "webhooks-created": "Webhooks created",
  "principal-requests": "Requests and grievances from principals",
};

const FILTERS: { key: "OPEN" | "ACKNOWLEDGED" | "ALL"; label: string }[] = [
  { key: "OPEN", label: "Open" },
  { key: "ACKNOWLEDGED", label: "Acknowledged" },
  { key: "ALL", label: "All" },
];

const fmt = (iso: string | null) => (iso ? new Date(iso).toLocaleString() : "—");

/** "400 in the hour, against about 4 usually" */
export const describeAnomaly = (a: Pick<AnomalyDto, "observed" | "expected">) =>
  `${a.observed} in the hour, against ${a.expected < 1 ? "under 1" : `about ${Math.round(a.expected)}`} usually`;

/**
 * Hours in which a fiduciary did far more of something than its own usual (ADR-112), found from the audit ledger's counts. A finding says
 * only that: it is not proof of abuse (a launch or a campaign looks the same), and it holds nothing about any principal. An administrator
 * looks at the fiduciary and acknowledges it once it is understood.
 */
export default function Anomalies({ session }: { session: Session }) {
  const [filter, setFilter] = useState<"OPEN" | "ACKNOWLEDGED" | "ALL">("OPEN");
  const [rows, setRows] = useState<AnomalyDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setRows(await anomalies.list(session.gwBase, await freshToken(session.auth), filter));
    } catch (err) {
      setError(friendlyError(err));
      setRows([]);
    }
  }, [session.auth, session.gwBase, filter]);

  useEffect(() => {
    setRows(null);
    load();
  }, [load]);

  const acknowledge = async (id: string) => {
    setBusy(id);
    setError(null);
    try {
      await anomalies.acknowledge(session.gwBase, await freshToken(session.auth), id);
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
        title="Unusual activity"
        subtitle="Hours in which a fiduciary did far more of something than it usually does. A launch or a campaign looks the same as abuse: look, then acknowledge."
      />
      <div className="cm-chips" role="group" aria-label="Show findings">
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
        <p className="cm-note">{filter === "OPEN" ? "Nothing unusual is open." : "No findings to show."}</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Fiduciary</th>
                <th>What</th>
                <th>Hour</th>
                <th>How much</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {rows.map((a) => (
                <tr key={a.id}>
                  <td>
                    <a href={tenantHref(a.dfId)}>
                      <code>{a.dfId.slice(0, 8)}</code>
                    </a>
                  </td>
                  <td>{SIGNAL_LABELS[a.signal] ?? a.signal}</td>
                  <td>{fmt(a.windowStart)}</td>
                  <td>{describeAnomaly(a)}</td>
                  <td>
                    <span className={`cm-badge cm-badge-${a.status === "OPEN" ? "failed" : "delivered"}`}>{a.status}</span>
                    {a.acknowledgedAt ? <div className="cm-note" style={{ margin: 0 }}>{fmt(a.acknowledgedAt)}</div> : null}
                  </td>
                  <td>
                    {a.status === "OPEN" && session.isAdmin ? (
                      <Button disabled={busy === a.id} onClick={() => acknowledge(a.id)}>
                        Acknowledge
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
