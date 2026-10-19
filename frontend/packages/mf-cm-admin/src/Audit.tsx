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
import { aud, dfReg, type AudActivityRow, type AudVerifyResult } from "api-client";
import { AuditTable, Button, Card, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { tenantHref } from "./routes";
import { freshToken, type Session } from "./session";

type Category = "ALL" | "PLATFORM" | "CONSENT";

const CATEGORIES: { key: Category; label: string }[] = [
  { key: "ALL", label: "Everything" },
  { key: "PLATFORM", label: "What people did" },
  { key: "CONSENT", label: "Consent lifecycle" },
];

const PAGE = 50;

/** The ledger's entries, newest first, with filters and older pages: every tenant's, or one's (`dfId`). */
export function AuditPanel({ session, dfId, names }: { session: Session; dfId?: string; names?: Record<string, string> }) {
  const [category, setCategory] = useState<Category>("ALL");
  const [rows, setRows] = useState<AudActivityRow[] | null>(null);
  const [more, setMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (before?: number) => {
      setError(null);
      try {
        const page = await aud.activity(session.gwBase, await freshToken(session.auth), { dfId, category, before, limit: PAGE });
        setRows((prev) => (before ? [...(prev ?? []), ...page] : page));
        setMore(page.length === PAGE);
      } catch (err) {
        setError(friendlyError(err));
        setRows((prev) => prev ?? []);
      }
    },
    [session, dfId, category],
  );

  useEffect(() => {
    setRows(null);
    load();
  }, [load]);

  return (
    <>
      <div className="cm-chips" role="group" aria-label="Show">
        {CATEGORIES.map((c) => (
          <button key={c.key} aria-pressed={category === c.key} onClick={() => setCategory(c.key)}>
            {c.label}
          </button>
        ))}
        <button onClick={() => load()} aria-label="Refresh">
          Refresh
        </button>
      </div>
      {error ? <ErrorBanner message={error} /> : null}
      {rows === null ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <p className="cm-note">Nothing recorded yet.</p>
      ) : (
        <AuditTable
          rows={rows}
          tenant={
            dfId
              ? undefined
              : (id) => (id ? <a href={tenantHref(id)}>{names?.[id] ?? id.slice(0, 8)}</a> : <span className="cm-note">Platform</span>)
          }
        />
      )}
      {more ? (
        <p>
          <Button onClick={() => load(rows?.[rows.length - 1]?.seq)}>Older entries</Button>
        </p>
      ) : null}
    </>
  );
}

function Verify({ session }: { session: Session }) {
  const [result, setResult] = useState<AudVerifyResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  return (
    <Card>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
        <div>
          <strong>Is the ledger intact?</strong>
          <div className="cm-note" style={{ margin: 0 }}>
            Recomputes every entry's hash in order, and checks each checkpoint's external timestamp.
          </div>
        </div>
        <Button
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              setResult(await aud.verify(session.gwBase, await freshToken(session.auth)));
            } catch (err) {
              setError(friendlyError(err));
            } finally {
              setBusy(false);
            }
          }}
        >
          {busy ? "Checking…" : "Verify the chain"}
        </Button>
      </div>
      {error ? <ErrorBanner message={error} /> : null}
      {result ? (
        <div className="cm-summary" style={{ marginTop: 12, marginBottom: 0 }}>
          <div className="cm-stat">
            <b className={result.chain.intact ? "cm-ok" : undefined}>{result.chain.intact ? "Intact" : "Broken"}</b>
            <span>
              {result.chain.entriesChecked.toLocaleString()} entries checked
              {result.chain.payloadsErased ? `, ${result.chain.payloadsErased} with details erased` : ""}
              {result.chain.intact ? "" : ` · first break at #${result.chain.firstBrokenSeq}: ${result.chain.reason}`}
            </span>
          </div>
          <div className="cm-stat">
            <b className={result.checkpoints.intact ? "cm-ok" : undefined}>{result.checkpoints.intact ? "Intact" : "Broken"}</b>
            <span>
              {result.checkpoints.checkpoints} checkpoints, {result.checkpoints.anchored} timestamped
              {result.checkpoints.intact ? "" : ` · ${result.checkpoints.reason}`}
            </span>
          </div>
        </div>
      ) : null}
    </Card>
  );
}

/** The platform's audit (ADR-014, ADR-040): what everyone did, on every tenant, in the tamper-evident ledger. */
export default function Audit({ session }: { session: Session }) {
  const [names, setNames] = useState<Record<string, string>>({});
  const [dfId, setDfId] = useState("");

  useEffect(() => {
    freshToken(session.auth)
      .then((token) => dfReg.list(session.gwBase, token))
      .then((list) => setNames(Object.fromEntries(list.map((df) => [df.id, df.name]))))
      .catch(() => {});
  }, [session]);

  return (
    <>
      <PageHeader
        title="Audit"
        subtitle="Everything recorded in the tamper-evident ledger: who changed what, on which tenant, and the consent lifecycle."
      />
      {session.isAdmin ? <Verify session={session} /> : null}
      <div style={{ margin: "16px 0 8px", maxWidth: 360 }}>
        <select value={dfId} onChange={(e) => setDfId(e.target.value)} aria-label="Tenant">
          <option value="">Every tenant, and the platform</option>
          {Object.entries(names)
            .sort((a, b) => a[1].localeCompare(b[1]))
            .map(([id, name]) => (
              <option key={id} value={id}>
                {name}
              </option>
            ))}
        </select>
      </div>
      <AuditPanel key={dfId} session={session} dfId={dfId || undefined} names={names} />
    </>
  );
}
