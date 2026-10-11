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
import {
  cnsCmd,
  cnsQry,
  dfReg,
  ApiError,
  type CnsArtRdMdl,
  type DfRole,
  type ExtDpRefTyp,
} from "api-client";
import { Button, Card, PageHeader, Field, Badge, ErrorBanner, Spinner, friendlyError } from "ui-kit/src/index";
import { ROLE_LABELS, managesTeam, raisesRequests } from "./roles";
import Team from "./Team";
import { freshToken } from "./token";

interface Props {
  auth: AuthInfo;
  dfId: string;
  role: DfRole;
  gwBase: string;
}

type View = "requests" | "team";

/** DF Console: a fiduciary's people, signed in as themselves (ADR-033), see what their role allows. */
export default function DfConsoleApp({ auth, dfId, role, gwBase }: Props) {
  const [view, setView] = useState<View>("requests");
  const [dfName, setDfName] = useState<string>(dfId);

  useEffect(() => {
    freshToken(auth)
      .then((token) => dfReg.getOne(gwBase, token, dfId))
      .then((df) => setDfName(df.name))
      .catch(() => {});
  }, [auth, dfId, gwBase]);

  return (
    <div className="cm-page">
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <PageHeader title={`DF Console — ${dfName}`} subtitle={`${auth.username ?? ""} · ${ROLE_LABELS[role]}`} />
        <Button onClick={auth.logout}>Sign out</Button>
      </div>
      <nav style={{ display: "flex", gap: 8, marginBottom: 16 }} aria-label="Sections">
        <Button variant={view === "requests" ? "primary" : undefined} onClick={() => setView("requests")}>
          Consent requests
        </Button>
        <Button variant={view === "team" ? "primary" : undefined} onClick={() => setView("team")}>
          Team
        </Button>
      </nav>
      {view === "requests" ? (
        <Requests auth={auth} role={role} gwBase={gwBase} />
      ) : (
        <Team auth={auth} role={role} gwBase={gwBase} canManage={managesTeam(role)} />
      )}
    </div>
  );
}

function Requests({ auth, role, gwBase }: { auth: AuthInfo; role: DfRole; gwBase: string }) {
  const [rows, setRows] = useState<CnsArtRdMdl[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [form, setForm] = useState({
    extDpRefTyp: "EMAIL" as ExtDpRefTyp,
    extDpRefVal: "",
    prpsCode: "ORDER_FULFILLMENT",
    dCatCodes: "EMAIL",
    ntcTitle: "",
    ntcText: "",
  });
  const [submitting, setSubmitting] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(await cnsQry.listMine(gwBase, await freshToken(auth)));
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  };

  // A freshly-created CnsReq is written synchronously on cm-cns-cmd-svc but only shows up
  // here once cm-cns-qry-svc's Pulsar-driven projector has processed CnsReqCreatedEvt — a
  // plain load() right after create() can run before that lands and silently show a stale
  // list. Poll briefly for the specific row rather than assume one refresh is enough.
  const loadUntilPresent = async (cnsReqId: string, attempt = 0) => {
    let data: CnsArtRdMdl[] | null = null;
    try {
      data = await cnsQry.listMine(gwBase, await freshToken(auth));
      setRows(data);
    } catch (err) {
      setError(friendlyError(err));
    }
    const found = data?.some((r) => r.cnsReqId === cnsReqId);
    if (!found && attempt < 6) {
      setTimeout(() => loadUntilPresent(cnsReqId, attempt + 1), 700);
    } else {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      {error ? <ErrorBanner message={error} /> : null}

      {raisesRequests(role) ? (
        <Card className="cm-card">
          <h3 style={{ marginTop: 0 }}>Raise a new consent request</h3>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))", gap: "0 16px" }}>
            <Field label="Data Principal reference type">
              <select value={form.extDpRefTyp} onChange={(e) => setForm({ ...form, extDpRefTyp: e.target.value as ExtDpRefTyp })}>
                <option value="EMAIL">Email</option>
                <option value="PHONE">Phone</option>
              </select>
            </Field>
            <Field label="Data Principal reference value">
              <input
                value={form.extDpRefVal}
                onChange={(e) => setForm({ ...form, extDpRefVal: e.target.value })}
                placeholder="dp-demo@cm.local"
              />
            </Field>
            <Field label="Purpose code">
              <input value={form.prpsCode} onChange={(e) => setForm({ ...form, prpsCode: e.target.value })} />
            </Field>
            <Field label="Data categories (comma-separated)">
              <input value={form.dCatCodes} onChange={(e) => setForm({ ...form, dCatCodes: e.target.value })} />
            </Field>
            <Field label="Notice title">
              <input value={form.ntcTitle} onChange={(e) => setForm({ ...form, ntcTitle: e.target.value })} />
            </Field>
            <Field label="Notice text">
              <input value={form.ntcText} onChange={(e) => setForm({ ...form, ntcText: e.target.value })} />
            </Field>
          </div>
          <Button
            variant="primary"
            disabled={submitting || !form.extDpRefVal || !form.prpsCode}
            onClick={async () => {
              setSubmitting(true);
              setError(null);
              try {
                const created = await cnsCmd.create(gwBase, await freshToken(auth), {
                  extDpRefTyp: form.extDpRefTyp,
                  extDpRefVal: form.extDpRefVal,
                  prpsCode: form.prpsCode,
                  dCatCodes: form.dCatCodes.split(",").map((s) => s.trim()).filter(Boolean),
                  ntcTitle: form.ntcTitle || form.prpsCode,
                  ntcText: form.ntcText || `We need your consent for ${form.prpsCode}.`,
                });
                setForm({ ...form, extDpRefVal: "", ntcTitle: "", ntcText: "" });
                setLoading(true);
                await loadUntilPresent(created.id);
              } catch (err) {
                if (err instanceof ApiError && err.status === 402) {
                  setError("Subscription inactive — this request was blocked by the billing eligibility gate.");
                } else if (err instanceof ApiError && err.status === 429) {
                  setError("Monthly quota exceeded — this request was blocked by the billing eligibility gate.");
                } else {
                  setError(friendlyError(err));
                }
              } finally {
                setSubmitting(false);
              }
            }}
          >
            Raise request
          </Button>
        </Card>
      ) : null}

      <div style={{ height: 20 }} />

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
        <h3 style={{ margin: 0 }}>Requests raised by this fiduciary</h3>
        <Button onClick={load}>Refresh</Button>
      </div>

      {loading ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <p style={{ color: "var(--cm-text-dim)" }}>No consent requests yet.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Purpose</th>
                <th>Data categories</th>
                <th>Status</th>
                <th>Updated</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.cnsReqId}>
                  <td>{row.prpsCode}</td>
                  <td>{row.dCatCodes.join(", ")}</td>
                  <td>
                    <Badge status={row.lcySts} />
                  </td>
                  <td>{new Date(row.updatedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
