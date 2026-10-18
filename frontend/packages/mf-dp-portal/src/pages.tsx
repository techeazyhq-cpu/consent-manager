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
import type { CnsArtRdMdl } from "api-client";
import { Button, Card, PageHeader, Spinner } from "ui-kit/src/index";
import ConsentCard, { DfLogo, statusOrder } from "./ConsentCard";
import PushToggle from "./PushToggle";
import { awaitsDecision } from "./labels";
import type { Portal } from "./portal";
import { orgHref } from "./routes";

function ConsentList({ portal, rows, showOrg = true, empty }: { portal: Portal; rows: CnsArtRdMdl[]; showOrg?: boolean; empty: string }) {
  if (rows.length === 0) return <p className="cm-note">{empty}</p>;
  return (
    <div className="dp-list">
      {rows.map((row) => (
        <ConsentCard key={row.cnsReqId} portal={portal} row={row} showOrg={showOrg} />
      ))}
    </div>
  );
}

/** The landing page: what needs a decision, the totals, and every organisation as a logo tile. */
export function Dashboard({ portal }: { portal: Portal }) {
  const [query, setQuery] = useState("");
  const rows = portal.rows;
  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return portal.orgs.filter((o) => !q || o.name.toLowerCase().includes(q));
  }, [portal.orgs, query]);

  if (!rows) return <Spinner label="Loading your consents…" />;
  const count = (pred: (r: CnsArtRdMdl) => boolean) => rows.filter(pred).length;

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Who you've shared your data with, and what you've agreed to." />

      {portal.awaiting.length > 0 ? (
        <div className="dp-callout" role="status">
          <span>
            <strong>{portal.awaiting.length}</strong>{" "}
            {portal.awaiting.length === 1 ? "request is" : "requests are"} waiting for your decision.
          </span>
          <a className="cm-btn cm-btn-primary" href="#/requests" style={{ textDecoration: "none" }}>
            Review {portal.awaiting.length === 1 ? "it" : "them"}
          </a>
        </div>
      ) : null}

      <div className="cm-summary">
        <a className="cm-stat" href="#/requests" style={{ color: "inherit", textDecoration: "none" }}>
          <b>{portal.awaiting.length}</b>
          <span>Awaiting your decision</span>
        </a>
        <a className="cm-stat" href="#/consents" style={{ color: "inherit", textDecoration: "none" }}>
          <b>{count((r) => r.lcySts === "ISSUED")}</b>
          <span>Consents given</span>
        </a>
        <div className="cm-stat">
          <b>{portal.orgs.length}</b>
          <span>Organisations</span>
        </div>
        <div className="cm-stat">
          <b>{count((r) => r.lcySts === "REVOKED" || r.lcySts === "DENIED")}</b>
          <span>Declined or withdrawn</span>
        </div>
      </div>

      {portal.orgs.length === 0 ? (
        <Card>
          <strong>Nothing here yet.</strong>
          <p className="cm-note">
            When an organisation asks for your consent using {portal.auth.email ?? "your email address"}, it appears
            here for you to decide.
          </p>
        </Card>
      ) : (
        <>
          <div className="cm-toolbar">
            <input
              type="search"
              placeholder="Search organisations"
              aria-label="Search organisations"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <Button onClick={portal.reload}>Refresh</Button>
          </div>
          {shown.length === 0 ? (
            <p className="cm-note">No organisations match your search.</p>
          ) : (
            <div className="cm-logo-tiles">
              {shown.map((org) => (
                <a key={org.dfId} className="cm-logo-tile" href={orgHref(org.dfId)} style={{ textDecoration: "none" }}>
                  <DfLogo portal={portal} dfId={org.dfId} />
                  <div className="cm-logo-tile-name">{org.name}</div>
                  <div className="dp-tile-counts">
                    <span>
                      <b>{org.given}</b> given
                    </span>
                    <span className={org.awaiting ? "dp-tile-awaiting" : undefined}>
                      <b>{org.awaiting}</b> awaiting you
                    </span>
                  </div>
                </a>
              ))}
            </div>
          )}
        </>
      )}
    </>
  );
}

/** Every request waiting for a decision, newest first. */
export function Requests({ portal }: { portal: Portal }) {
  if (!portal.rows) return <Spinner />;
  return (
    <>
      <PageHeader
        title="Requests"
        subtitle="Organisations asking for your consent. Nothing is shared until you give it, and you can withdraw it later."
      />
      <ConsentList portal={portal} rows={portal.awaiting} empty="No requests are waiting for you." />
    </>
  );
}

type Filter = "all" | "given" | "awaiting" | "ended";

const FILTERS: { key: Filter; label: string; test: (r: CnsArtRdMdl) => boolean }[] = [
  { key: "all", label: "All", test: () => true },
  { key: "given", label: "Given", test: (r) => r.lcySts === "ISSUED" },
  { key: "awaiting", label: "Awaiting you", test: (r) => awaitsDecision(r.lcySts) },
  { key: "ended", label: "Declined, withdrawn or expired", test: (r) => ["DENIED", "REVOKED", "EXPIRED"].includes(r.lcySts) },
];

function sortForPerson(rows: CnsArtRdMdl[]): CnsArtRdMdl[] {
  return [...rows].sort((a, b) => statusOrder(a.lcySts) - statusOrder(b.lcySts) || b.updatedAt.localeCompare(a.updatedAt));
}

/** Every consent, given or not, with a filter. */
export function Consents({ portal }: { portal: Portal }) {
  const [filter, setFilter] = useState<Filter>("all");
  if (!portal.rows) return <Spinner />;
  const rows = sortForPerson(portal.rows.filter(FILTERS.find((f) => f.key === filter)!.test));
  return (
    <>
      <PageHeader title="My consents" subtitle="Everything you've been asked, and what you decided." />
      <div className="dp-filters" role="group" aria-label="Show">
        {FILTERS.map((f) => (
          <button key={f.key} aria-pressed={filter === f.key} onClick={() => setFilter(f.key)}>
            {f.label} ({portal.rows!.filter(f.test).length})
          </button>
        ))}
      </div>
      <ConsentList portal={portal} rows={rows} empty="Nothing to show here." />
    </>
  );
}

/** One organisation: what it is waiting for, what you've given it, and the history. */
export function OrgDetail({ portal, dfId }: { portal: Portal; dfId: string }) {
  if (!portal.rows) return <Spinner />;
  const rows = portal.rows.filter((r) => r.dfId === dfId);
  const awaiting = rows.filter((r) => awaitsDecision(r.lcySts));
  const given = rows.filter((r) => r.lcySts === "ISSUED");
  const ended = sortForPerson(rows.filter((r) => !awaitsDecision(r.lcySts) && r.lcySts !== "ISSUED"));

  return (
    <>
      <a className="cm-back" href="#/dashboard">
        ← Dashboard
      </a>
      <div className="cm-head">
        <DfLogo portal={portal} dfId={dfId} size={84} />
        <div style={{ minWidth: 0 }}>
          <h1>{portal.orgName(dfId)}</h1>
          <p>
            {given.length} given · {awaiting.length} awaiting you · {ended.length} ended
          </p>
        </div>
      </div>
      {rows.length === 0 ? (
        <p className="cm-note">You have no consent activity with this organisation.</p>
      ) : (
        <>
          {awaiting.length ? (
            <>
              <h3 className="cm-section-title">Awaiting your decision</h3>
              <ConsentList portal={portal} rows={awaiting} showOrg={false} empty="" />
            </>
          ) : null}
          <h3 className="cm-section-title">Consents you've given</h3>
          <ConsentList portal={portal} rows={given} showOrg={false} empty="None at the moment." />
          {ended.length ? (
            <>
              <h3 className="cm-section-title">Declined, withdrawn or expired</h3>
              <ConsentList portal={portal} rows={ended} showOrg={false} empty="" />
            </>
          ) : null}
        </>
      )}
    </>
  );
}

export function Notifications({ portal }: { portal: Portal }) {
  return (
    <>
      <PageHeader title="Notifications" subtitle="Hear about new requests as soon as an organisation asks." />
      <Card>
        <p style={{ marginTop: 0 }}>
          When an organisation asks for your consent, it tells you by email or text message, at the address or number
          it has for you. You can also get a notification in this browser.
        </p>
        <PushToggle auth={portal.auth} gwBase={portal.gwBase} onError={portal.setError} />
        <p className="cm-note">
          Signing out turns notifications off for this browser, so a shared computer doesn't keep receiving yours.
        </p>
      </Card>
    </>
  );
}

export function Account({ portal, onSignOut }: { portal: Portal; onSignOut: () => void }) {
  return (
    <>
      <PageHeader title="Account" subtitle="You sign in with a one-time code sent to your email address." />
      <Card style={{ marginBottom: 18 }}>
        <dl className="cm-fields">
          <div>
            <dt>Signed in as</dt>
            <dd>{portal.auth.username ?? portal.auth.email}</dd>
          </div>
          <div>
            <dt>Email</dt>
            <dd>{portal.auth.email ?? "—"}</dd>
          </div>
        </dl>
      </Card>
      <p className="cm-note">
        Organisations find you by this email address. Requests sent to it before you first signed in are already
        here.
      </p>
      <Button onClick={onSignOut}>Sign out</Button>
    </>
  );
}
