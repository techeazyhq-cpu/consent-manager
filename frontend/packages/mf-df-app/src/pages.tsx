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

import { useMemo, useState } from "react";
import { dfReg, type CnsArtRdMdl, type CnsLcySts } from "api-client";
import { Button, Card, OrgLogo, PageHeader, Spinner, humanise } from "ui-kit/src/index";
import type { Console } from "./console";
import { COUNTS, STATUS, countOf, fmtDate, grantRate } from "./labels";
import { ROLE_HINTS, ROLE_LABELS, raisesRequests } from "./roles";
import { purposeHref, raiseHref } from "./routes";
import { freshToken } from "./token";

export function StatusPill({ sts }: { sts: CnsLcySts }) {
  const { label, badge } = STATUS[sts];
  return <span className={`cm-badge cm-badge-${badge.toLowerCase()}`}>{label}</span>;
}

export function OrgMark({ console: c, size }: { console: Console; size?: number }) {
  return (
    <OrgLogo
      id={c.dfId}
      name={c.dfName}
      size={size}
      load={async () => dfReg.logo(c.gwBase, await freshToken(c.auth), c.dfId)}
    />
  );
}

/** The counts by status; each one opens the requests with that status. */
function Counts({ rows, onPick }: { rows: CnsArtRdMdl[]; onPick?: (sts?: CnsLcySts[]) => void }) {
  return (
    <div className="cm-summary">
      {COUNTS.map(({ key, label, sts }) =>
        onPick ? (
          <button key={key} className="cm-stat df-stat-button" onClick={() => onPick(sts)}>
            <b>{countOf(rows, sts)}</b>
            <span>{label}</span>
          </button>
        ) : (
          <div key={key} className="cm-stat">
            <b>{countOf(rows, sts)}</b>
            <span>{label}</span>
          </div>
        ),
      )}
    </div>
  );
}

function RaiseButton({ console: c, prpsCode }: { console: Console; prpsCode?: string }) {
  if (!raisesRequests(c.role)) return null;
  return (
    <a className="cm-btn cm-btn-primary" href={raiseHref(prpsCode)} style={{ textDecoration: "none" }}>
      Raise a request
    </a>
  );
}

/** The landing page: how requests are going, and every purpose asked for as a tile. */
export function Dashboard({ console: c, onFilter }: { console: Console; onFilter: (sts?: CnsLcySts[]) => void }) {
  const rows = c.rows;
  if (!rows) return <Spinner label="Loading requests…" />;
  const failed = countOf(rows, ["NOTIFICATION_FAILED"]);
  const rate = grantRate(rows);

  return (
    <>
      <PageHeader title="Dashboard" subtitle="How your consent requests are going, purpose by purpose." />

      {failed > 0 ? (
        <div className="df-callout" role="status">
          <span>
            <strong>{failed}</strong> {failed === 1 ? "person" : "people"} couldn't be notified of your request. Check
            the contact details you used.
          </span>
          <Button onClick={() => onFilter(["NOTIFICATION_FAILED"])}>See them</Button>
        </div>
      ) : null}

      <Counts rows={rows} onPick={onFilter} />
      {rate !== null ? <p className="cm-note">Of the people who decided, {rate}% granted consent.</p> : null}

      <div className="cm-toolbar" style={{ marginTop: 18 }}>
        <h3 style={{ margin: 0, marginRight: "auto" }}>Purposes</h3>
        <Button onClick={c.reload}>Refresh</Button>
        <RaiseButton console={c} />
      </div>
      {c.purposes.length === 0 ? (
        <Card>
          <strong>No requests yet.</strong>
          <p className="cm-note">
            {raisesRequests(c.role)
              ? "Raise your first consent request: say what you need the data for, which data, and the notice people will read."
              : "Requests your organisation raises will appear here."}
          </p>
        </Card>
      ) : (
        <div className="cm-logo-tiles">
          {c.purposes.map((p) => (
            <a key={p.code} className="cm-logo-tile" href={purposeHref(p.code)} style={{ textDecoration: "none" }}>
              <OrgLogo id={`prps:${p.code}`} name={humanise(p.code)} load={async () => null} size={56} />
              <div className="cm-logo-tile-name">{humanise(p.code)}</div>
              <div className="df-tile-counts">
                <span>
                  <b>{p.sent}</b> sent
                </span>
                <span>
                  <b>{p.granted}</b> granted
                </span>
                <span>
                  <b>{p.pending}</b> awaiting
                </span>
              </div>
            </a>
          ))}
        </div>
      )}
    </>
  );
}

function RequestTable({ rows }: { rows: CnsArtRdMdl[] }) {
  if (rows.length === 0) return <p className="cm-note">No requests to show.</p>;
  return (
    <div style={{ overflowX: "auto" }}>
      <table className="cm-table">
        <thead>
          <tr>
            <th>Purpose</th>
            <th>Data</th>
            <th>Notice</th>
            <th>Status</th>
            <th>Raised</th>
            <th>Valid until</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.cnsReqId}>
              <td>
                <a href={purposeHref(r.prpsCode)}>{humanise(r.prpsCode)}</a>
              </td>
              <td>{r.dCatCodes.map(humanise).join(", ")}</td>
              <td title={r.ntcText ?? undefined}>{r.ntcTitle ?? "—"}</td>
              <td>
                <StatusPill sts={r.lcySts} />
              </td>
              <td>{fmtDate(r.createdAt)}</td>
              <td>{r.lcySts === "ISSUED" ? fmtDate(r.validUntil) : "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

const FILTERS: { key: string; label: string; sts?: CnsLcySts[] }[] = [
  { key: "all", label: "All" },
  ...COUNTS.filter((c) => c.sts).map((c) => ({ key: c.key, label: c.label, sts: c.sts })),
];

/** Every request the organisation raised, newest first, by status and purpose. */
export function Requests({ console: c, initial }: { console: Console; initial?: CnsLcySts[] }) {
  const [filter, setFilter] = useState<string>(() => FILTERS.find((f) => f.sts?.join() === initial?.join())?.key ?? "all");
  const [query, setQuery] = useState("");
  const rows = c.rows;
  const shown = useMemo(() => {
    const sts = FILTERS.find((f) => f.key === filter)?.sts;
    const q = query.trim().toLowerCase();
    return (rows ?? [])
      .filter((r) => (!sts || sts.includes(r.lcySts)) && (!q || humanise(r.prpsCode).toLowerCase().includes(q)))
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  }, [rows, filter, query]);

  if (!rows) return <Spinner />;
  return (
    <>
      <PageHeader title="Requests" subtitle="Every consent request your organisation has raised." />
      <div className="cm-toolbar">
        <input type="search" placeholder="Search by purpose" aria-label="Search by purpose" value={query} onChange={(e) => setQuery(e.target.value)} />
        <Button onClick={c.reload}>Refresh</Button>
        <RaiseButton console={c} />
      </div>
      <div className="df-filters" role="group" aria-label="Show">
        {FILTERS.map((f) => (
          <button key={f.key} aria-pressed={filter === f.key} onClick={() => setFilter(f.key)}>
            {f.label} ({countOf(rows, f.sts)})
          </button>
        ))}
      </div>
      <RequestTable rows={shown} />
    </>
  );
}

/** One purpose: its numbers, the notices used for it, and its requests. */
export function PurposeDetail({ console: c, prpsCode }: { console: Console; prpsCode: string }) {
  if (!c.rows) return <Spinner />;
  const rows = c.rows.filter((r) => r.prpsCode === prpsCode).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const rate = grantRate(rows);
  const notices = Array.from(new Set(rows.map((r) => r.ntcTitle).filter((t): t is string => !!t)));

  return (
    <>
      <a className="cm-back" href="#/dashboard">
        ← Dashboard
      </a>
      <div className="cm-head">
        <OrgLogo id={`prps:${prpsCode}`} name={humanise(prpsCode)} load={async () => null} size={72} />
        <div style={{ minWidth: 0 }}>
          <h1>{humanise(prpsCode)}</h1>
          <p>
            <code>{prpsCode}</code>
            {rate !== null ? ` · ${rate}% of people who decided granted consent` : ""}
          </p>
        </div>
        <div style={{ marginLeft: "auto" }}>
          <RaiseButton console={c} prpsCode={prpsCode} />
        </div>
      </div>
      <Counts rows={rows} />
      {notices.length ? (
        <p className="cm-note">Notices used: {notices.map((n) => `“${n}”`).join(", ")}</p>
      ) : null}
      <h3 className="cm-section-title">Requests</h3>
      <RequestTable rows={rows} />
    </>
  );
}

export function Organisation({ console: c }: { console: Console }) {
  return (
    <>
      <PageHeader title="Organisation" subtitle="How the Consent Manager knows your organisation." />
      <div className="cm-head">
        <OrgMark console={c} size={84} />
        <div>
          <h1>{c.dfName}</h1>
          <p>Your name and logo as people see them when you ask for consent.</p>
        </div>
      </div>
      <Card>
        <dl className="cm-fields">
          <div>
            <dt>Organisation ID</dt>
            <dd>
              <code>{c.dfId}</code>
            </dd>
          </div>
          <div>
            <dt>API client for your systems</dt>
            <dd>
              <code>df-api-{c.dfId}</code>
            </dd>
          </div>
        </dl>
        <p className="cm-note" style={{ marginBottom: 0 }}>
          Your systems raise requests and check consent through the API with this client and its secret, shown once
          when your organisation was activated. To change your name or logo, contact the platform; to change your plan, see{" "}
          <a href="#/billing">Billing</a>.
        </p>
      </Card>
    </>
  );
}

export function Account({ console: c }: { console: Console }) {
  return (
    <>
      <PageHeader title="Account" subtitle="You sign in with a one-time code sent to your email address." />
      <Card style={{ marginBottom: 18 }}>
        <dl className="cm-fields">
          <div>
            <dt>Signed in as</dt>
            <dd>{c.auth.username ?? c.auth.email}</dd>
          </div>
          <div>
            <dt>Organisation</dt>
            <dd>{c.dfName}</dd>
          </div>
          <div>
            <dt>Your role</dt>
            <dd>
              {ROLE_LABELS[c.role]} — {ROLE_HINTS[c.role]}
            </dd>
          </div>
        </dl>
      </Card>
      <Button onClick={c.auth.logout}>Sign out</Button>
    </>
  );
}
