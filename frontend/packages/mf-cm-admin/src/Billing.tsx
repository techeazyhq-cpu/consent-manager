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

import { useEffect, useMemo, useState } from "react";
import { bil, dfReg, formatMinor, invoices, type BilOverviewRow, type InvoiceDto, type PlanPriceDto, type PlnChgReqDto, type PlnChgReqSts, type SubPln, type UsgAlertDto } from "api-client";
import { Badge, Button, Card, ErrorBanner, PageHeader, Spinner, UsageMeter, friendlyError, shareUsed } from "ui-kit/src/index";
import { BILLING_TABS, billingHref, tenantHref, type BillingTab } from "./routes";
import { freshToken, type Session } from "./session";

const TAB_LABELS: Record<BillingTab, string> = { overview: "Overview", requests: "Plan change requests", plans: "Plans", invoices: "Invoices" };

const fmt = (iso: string | null) =>
  iso ? new Date(iso).toLocaleString(undefined, { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" }) : "—";

/** Tenants by id, to show names: billing knows only ids. */
function useTenantNames(session: Session) {
  const [names, setNames] = useState<Record<string, string>>({});
  useEffect(() => {
    freshToken(session.auth)
      .then((token) => dfReg.list(session.gwBase, token))
      .then((list) => setNames(Object.fromEntries(list.map((df) => [df.id, df.name]))))
      .catch(() => {});
  }, [session]);
  return names;
}

function Overview({ session, names }: { session: Session; names: Record<string, string> }) {
  const [rows, setRows] = useState<BilOverviewRow[] | null>(null);
  const [alerts, setAlerts] = useState<UsgAlertDto[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    freshToken(session.auth)
      .then((token) => Promise.all([bil.overview(session.gwBase, token), bil.alerts(session.gwBase, token)]))
      .then(([overview, thisMonth]) => {
        setRows(overview);
        setAlerts(thisMonth);
      })
      .catch((err) => {
        setError(friendlyError(err));
        setRows([]);
      });
  }, [session]);

  // the highest alert each tenant has raised this month (ADR-060)
  const alertOf = useMemo(() => {
    const top = new Map<string, UsgAlertDto>();
    for (const a of alerts) {
      const seen = top.get(a.dfId);
      if (!seen || a.threshold > seen.threshold) top.set(a.dfId, a);
    }
    return top;
  }, [alerts]);

  const byPlan = useMemo(() => {
    const totals = new Map<string, { tenants: number; used: number }>();
    for (const r of rows ?? []) {
      const t = totals.get(r.subPlnName) ?? { tenants: 0, used: 0 };
      totals.set(r.subPlnName, { tenants: t.tenants + 1, used: t.used + r.curPeriodUsg });
    }
    return Array.from(totals.entries());
  }, [rows]);

  if (rows === null) return <Spinner />;
  const near = rows.filter((r) => shareUsed(r.curPeriodUsg, r.reqPerMonthQta) >= 80).length;
  const suspended = rows.filter((r) => r.dfSubSts === "SUSPENDED").length;

  return (
    <>
      {error ? <ErrorBanner message={error} /> : null}
      <div className="cm-summary">
        <div className="cm-stat">
          <b>{rows.length}</b>
          <span>Subscriptions</span>
        </div>
        <div className="cm-stat">
          <b>{near}</b>
          <span>At 80% or more of their allowance</span>
        </div>
        <div className="cm-stat">
          <b>{alertOf.size}</b>
          <span>Alerted this month</span>
        </div>
        <div className="cm-stat">
          <b>{suspended}</b>
          <span>Suspended</span>
        </div>
        {byPlan.map(([plan, t]) => (
          <div key={plan} className="cm-stat">
            <b>{t.tenants}</b>
            <span>
              On {plan} · {t.used.toLocaleString()} calls this month
            </span>
          </div>
        ))}
      </div>
      {rows.length === 0 ? (
        <p className="cm-note">No subscriptions yet: a tenant's starts when it's activated.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Tenant</th>
                <th>Plan</th>
                <th>Status</th>
                <th style={{ minWidth: 220 }}>This month</th>
                <th>Per second</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.dfId}>
                  <td>
                    <a href={tenantHref(r.dfId, "subscription")}>{names[r.dfId] ?? r.dfId}</a>
                  </td>
                  <td>{r.subPlnName}</td>
                  <td>
                    <Badge status={r.dfSubSts} />
                  </td>
                  <td>
                    {r.curPeriodUsg.toLocaleString()} of {r.reqPerMonthQta.toLocaleString()} (
                    {shareUsed(r.curPeriodUsg, r.reqPerMonthQta)}%)
                    <UsageMeter used={r.curPeriodUsg} of={r.reqPerMonthQta} label={`Allowance used by ${names[r.dfId] ?? r.dfId}`} />
                    {alertOf.get(r.dfId) ? (
                      <div className="cm-note" style={{ margin: 0 }}>
                        {alertOf.get(r.dfId)!.threshold >= 100 ? "Used up" : `Passed ${alertOf.get(r.dfId)!.threshold}%`}
                        , emailed {new Date(alertOf.get(r.dfId)!.raisedAt).toLocaleDateString()}
                      </div>
                    ) : null}
                  </td>
                  <td>{r.reqPerSecQta.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

/** One waiting request with its decision controls: administrators decide, operators see. */
export function PendingRequest({
  session,
  req,
  tenant,
  onDecided,
}: {
  session: Session;
  req: PlnChgReqDto;
  tenant?: string;
  onDecided: () => void;
}) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const decide = async (approve: boolean) => {
    if (!approve && !reason.trim()) {
      setError("Say why, so the fiduciary knows what to do next.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const token = await freshToken(session.auth);
      if (approve) await bil.approve(session.gwBase, token, req.id, reason.trim() || undefined);
      else await bil.decline(session.gwBase, token, req.id, reason.trim());
      onDecided();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      {error ? <ErrorBanner message={error} /> : null}
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <div>
          {tenant !== undefined ? (
            <strong>
              <a href={tenantHref(req.dfId, "subscription")}>{tenant}</a>
            </strong>
          ) : null}
          <div>
            {req.fromPlnName} → <b>{req.toPlnName}</b>
          </div>
          <div className="cm-note" style={{ margin: 0 }}>
            Asked by {req.requestedByName ?? "someone at the fiduciary"} · {fmt(req.requestedAt)}
          </div>
          {req.note ? <p style={{ margin: "8px 0 0" }}>“{req.note}”</p> : null}
        </div>
        <Badge status={req.sts} />
      </div>
      {session.isAdmin ? (
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 12 }}>
          <input
            style={{ flex: "1 1 260px" }}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="A note for the fiduciary (needed to decline)"
            maxLength={500}
            aria-label="Note for the fiduciary"
          />
          <Button variant="primary" disabled={busy} onClick={() => decide(true)}>
            Approve: move to {req.toPlnName}
          </Button>
          <Button variant="danger" disabled={busy} onClick={() => decide(false)}>
            Decline
          </Button>
        </div>
      ) : (
        <p className="cm-note">A platform administrator approves or declines plan changes.</p>
      )}
    </Card>
  );
}

const REQUEST_FILTERS: { key: PlnChgReqSts | "ALL"; label: string }[] = [
  { key: "PENDING", label: "Waiting" },
  { key: "APPROVED", label: "Approved" },
  { key: "DECLINED", label: "Declined" },
  { key: "WITHDRAWN", label: "Withdrawn" },
  { key: "ALL", label: "All" },
];

function Requests({ session, names }: { session: Session; names: Record<string, string> }) {
  const [filter, setFilter] = useState<PlnChgReqSts | "ALL">("PENDING");
  const [reqs, setReqs] = useState<PlnChgReqDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = async () => {
    setError(null);
    try {
      setReqs(await bil.requests(session.gwBase, await freshToken(session.auth), filter === "ALL" ? undefined : filter));
    } catch (err) {
      setError(friendlyError(err));
      setReqs([]);
    }
  };

  useEffect(() => {
    setReqs(null);
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter]);

  return (
    <>
      <div className="cm-chips" role="group" aria-label="Show requests">
        {REQUEST_FILTERS.map((f) => (
          <button key={f.key} aria-pressed={filter === f.key} onClick={() => setFilter(f.key)}>
            {f.label}
          </button>
        ))}
      </div>
      {error ? <ErrorBanner message={error} /> : null}
      {reqs === null ? (
        <Spinner />
      ) : reqs.length === 0 ? (
        <p className="cm-note">{filter === "PENDING" ? "No requests waiting." : "No requests to show."}</p>
      ) : filter === "PENDING" ? (
        <div style={{ display: "grid", gap: 12 }}>
          {reqs.map((r) => (
            <PendingRequest key={r.id} session={session} req={r} tenant={names[r.dfId] ?? r.dfId} onDecided={load} />
          ))}
        </div>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Tenant</th>
                <th>Change</th>
                <th>Asked</th>
                <th>Status</th>
                <th>Decided</th>
                <th>Note</th>
              </tr>
            </thead>
            <tbody>
              {reqs.map((r) => (
                <tr key={r.id}>
                  <td>
                    <a href={tenantHref(r.dfId, "subscription")}>{names[r.dfId] ?? r.dfId}</a>
                  </td>
                  <td>
                    {r.fromPlnName} → {r.toPlnName}
                  </td>
                  <td>
                    {fmt(r.requestedAt)}
                    <div className="cm-note" style={{ margin: 0 }}>
                      {r.requestedByName}
                    </div>
                  </td>
                  <td>
                    <Badge status={r.sts} />
                  </td>
                  <td>
                    {fmt(r.decidedAt)}
                    <div className="cm-note" style={{ margin: 0 }}>
                      {r.decidedByName}
                    </div>
                  </td>
                  <td>{r.decisionNote ?? r.note ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function Plans({ session }: { session: Session }) {
  const [plans, setPlans] = useState<SubPln[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    freshToken(session.auth)
      .then((token) => bil.listSubPlns(session.gwBase, token))
      .then((list) => setPlans([...list].sort((a, b) => a.reqPerMonthQta - b.reqPerMonthQta)))
      .catch((err) => {
        setError(friendlyError(err));
        setPlans([]);
      });
  }, [session]);

  if (plans === null) return <Spinner />;
  return (
    <>
      {error ? <ErrorBanner message={error} /> : null}
      <div className="cm-summary">
        {plans.map((p) => (
          <div key={p.id} className="cm-stat">
            <b>{p.name}</b>
            <span>
              {p.reqPerMonthQta.toLocaleString()} API calls a month · {p.reqPerSecQta.toLocaleString()} a second
            </span>
          </div>
        ))}
      </div>
      <p className="cm-note">
        Plans set quotas only: the platform doesn't charge or invoice through Consent Manager. They're defined in cm-bil-svc's
        schema.
      </p>
    </>
  );
}

/** The platform's billing (ADR-039): every tenant's usage, the plan change requests, and the plans. */
/** The last month that has ended, as yyyy-MM (UTC): the one invoices are issued for. */
export function lastMonth(now: Date = new Date()): string {
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - 1, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

/** A price typed as major units ("2999.50") in minor units (299950); null when it is not a non-negative amount with at most two decimals. */
export function parsePrice(text: string): number | null {
  const t = text.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
  const [whole, frac = ""] = t.split(".");
  return Number(whole) * 100 + Number(frac.padEnd(2, "0"));
}

/** Plan prices, the month's invoices, and what staff do with them (ADR-079). Changing anything is for administrators. */
function Invoices({ session, names }: { session: Session; names: Record<string, string> }) {
  const [month, setMonth] = useState(lastMonth());
  const [list, setList] = useState<InvoiceDto[] | null>(null);
  const [prices, setPrices] = useState<PlanPriceDto[]>([]);
  const [draft, setDraft] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState<string | null>(null);

  const load = async () => {
    try {
      const token = await freshToken(session.auth);
      const [l, p] = await Promise.all([invoices.all(session.gwBase, token, { month }), invoices.prices(session.gwBase, token)]);
      setList(l);
      setPrices(p);
    } catch (err) {
      setError(friendlyError(err));
      setList((prev) => prev ?? []);
    }
  };
  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, month]);

  const run = async (action: (token: string) => Promise<unknown>, done?: string) => {
    setBusy(true);
    setError(null);
    setNote(null);
    try {
      await action(await freshToken(session.auth));
      if (done) setNote(done);
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const download = async () => {
    try {
      const blob = await invoices.exportCsv(session.gwBase, await freshToken(session.auth), month);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `invoices-${month}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch (err) {
      setError(friendlyError(err));
    }
  };

  return (
    <>
      {error ? <ErrorBanner message={error} /> : null}
      {note ? <p className="cm-note">{note}</p> : null}

      <h3 className="cm-section-title">Monthly price of each plan</h3>
      <p className="cm-note">Invoices are worked out from these prices and from which plan each tenant was on, day by day.</p>
      <div style={{ overflowX: "auto" }}>
        <table className="cm-table">
          <thead>
            <tr>
              <th>Plan</th>
              <th>Now</th>
              <th>New price</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {prices.map((p) => {
              const typed = draft[p.planName] ?? "";
              const minor = parsePrice(typed);
              return (
                <tr key={p.planName}>
                  <td>
                    <strong>{p.planName}</strong>
                  </td>
                  <td>{(p.monthlyMinor / 100).toFixed(2)}</td>
                  <td>
                    <input
                      aria-label={`New monthly price of ${p.planName}`}
                      value={typed}
                      inputMode="decimal"
                      placeholder="e.g. 2999.00"
                      onChange={(e) => setDraft({ ...draft, [p.planName]: e.target.value })}
                    />
                  </td>
                  <td>
                    <Button
                      disabled={busy || minor === null}
                      onClick={() =>
                        run(async (t) => {
                          await invoices.setPrice(session.gwBase, t, p.planName, minor as number);
                          setDraft({ ...draft, [p.planName]: "" });
                        }, `${p.planName} is now ${(minor as number) / 100} a month, from the next invoice run.`)
                      }
                    >
                      Set
                    </Button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      <h3 className="cm-section-title">Invoices</h3>
      <div className="cm-toolbar">
        <label>
          Month <input type="month" value={month} max={lastMonth()} onChange={(e) => e.target.value && setMonth(e.target.value)} />
        </label>
        <Button
          disabled={busy}
          onClick={() =>
            run(async (t) => {
              const made = await invoices.run(session.gwBase, t, month);
              setNote(made.length ? `Issued ${made.length} invoice(s) for ${month}.` : `Nothing new to invoice for ${month}.`);
            })
          }
        >
          Issue this month's invoices
        </Button>
        <Button onClick={download}>Download CSV</Button>
      </div>
      <p className="cm-note">Months are calendar months in UTC. They are also issued automatically after each month ends.</p>

      {list === null ? (
        <Spinner />
      ) : list.length === 0 ? (
        <p className="cm-note">No invoices for {month}.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Number</th>
                <th>Tenant</th>
                <th>Total</th>
                <th>Status</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {list.map((i) => (
                <tr key={i.id}>
                  <td>
                    {i.number}
                    {i.kind === "CREDIT_NOTE" ? <div className="cm-note" style={{ margin: 0 }}>Credit note: {i.reason}</div> : null}
                  </td>
                  <td>
                    <a href={tenantHref(i.fiduciaryId)}>{names[i.fiduciaryId] ?? i.fiduciaryId}</a>
                  </td>
                  <td>{formatMinor(i.totalMinor, i.currency)}</td>
                  <td>
                    <Badge status={i.status} />
                    {i.paidNote ? <div className="cm-note" style={{ margin: 0 }}>{i.paidNote}</div> : null}
                  </td>
                  <td>
                    {i.kind === "INVOICE" && i.status === "ISSUED" ? (
                      <Button
                        disabled={busy}
                        onClick={() => {
                          const how = window.prompt(`Mark ${i.number} paid. How was it paid (reference)?`, "");
                          if (how !== null) run((t) => invoices.markPaid(session.gwBase, t, i.id, how));
                        }}
                      >
                        Mark paid
                      </Button>
                    ) : null}{" "}
                    {i.kind === "INVOICE" && i.status !== "CREDITED" ? (
                      <Button
                        disabled={busy}
                        onClick={() => {
                          const why = window.prompt(`Credit ${i.number} in full. Why?`, "");
                          if (why) run((t) => invoices.creditNote(session.gwBase, t, i.id, why));
                        }}
                      >
                        Credit
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

export default function Billing({ session, tab }: { session: Session; tab: BillingTab }) {
  const names = useTenantNames(session);
  return (
    <>
      <PageHeader title="Billing" subtitle="Every tenant's plan and usage this month, and the plan changes they've asked for." />
      <div className="cm-tabs" role="tablist" aria-label="Billing">
        {BILLING_TABS.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => {
              window.location.hash = billingHref(t);
            }}
          >
            {TAB_LABELS[t]}
          </button>
        ))}
      </div>
      {tab === "overview" ? <Overview session={session} names={names} /> : null}
      {tab === "requests" ? <Requests session={session} names={names} /> : null}
      {tab === "plans" ? <Plans session={session} /> : null}
      {tab === "invoices" ? <Invoices session={session} names={names} /> : null}
    </>
  );
}
