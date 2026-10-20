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
import { bil, myBilling, type DfSubStatusResult, type PlnChgReqDto, type SubPln } from "api-client";
import { Badge, Button, Card, ErrorBanner, PageHeader, Spinner, UsageMeter, friendlyError, shareUsed } from "ui-kit/src/index";
import type { Console } from "./console";
import { fmtDate } from "./labels";
import { managesTeam } from "./roles";
import { freshToken } from "./token";

/** What the meter says about this month, in words. */
export function usageNote(s: Pick<DfSubStatusResult, "curPeriodUsg" | "reqPerMonthQta" | "resetsAt">): string {
  const left = Math.max(0, s.reqPerMonthQta - s.curPeriodUsg);
  const resets = fmtDate(s.resetsAt);
  if (left === 0) return `Your allowance is used up: new requests are refused until it resets on ${resets}.`;
  if (shareUsed(s.curPeriodUsg, s.reqPerMonthQta) >= 80) {
    return `${left.toLocaleString()} left this month, so you're close to your allowance. It resets on ${resets}.`;
  }
  return `${left.toLocaleString()} left this month. It resets on ${resets}.`;
}

function AskForPlan({ console: c, plan, onAsked }: { console: Console; plan: SubPln; onAsked: () => void }) {
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) return <Button onClick={() => setOpen(true)}>Ask for {plan.name}</Button>;
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
          await myBilling.requestPlan(c.gwBase, await freshToken(c.auth), plan.name, note.trim() || undefined);
          onAsked();
        } catch (err) {
          setError(friendlyError(err));
        } finally {
          setBusy(false);
        }
      }}
      style={{ display: "grid", gap: 8 }}
    >
      {error ? <ErrorBanner message={error} /> : null}
      <textarea
        rows={2}
        maxLength={500}
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="Why you need it, for the platform (optional)"
        aria-label={`Note with your request for ${plan.name}`}
      />
      <div style={{ display: "flex", gap: 8 }}>
        <Button type="submit" variant="primary" disabled={busy}>
          {busy ? "Sending…" : `Send the request`}
        </Button>
        <Button type="button" onClick={() => setOpen(false)}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

/**
 * The fiduciary's billing (ADR-039): its plan, this month's usage against the allowance, and the plans it can ask
 * for. Owners and Administrators ask; a platform administrator approves or declines. Everyone at the fiduciary sees it.
 */
export default function Billing({ console: c }: { console: Console }) {
  const [sub, setSub] = useState<DfSubStatusResult | null>(null);
  const [plans, setPlans] = useState<SubPln[]>([]);
  const [reqs, setReqs] = useState<PlnChgReqDto[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const mayAsk = managesTeam(c.role);

  const load = useCallback(async () => {
    setError(null);
    try {
      const token = await freshToken(c.auth);
      const [s, p, r] = await Promise.all([
        myBilling.subscription(c.gwBase, token),
        bil.listSubPlns(c.gwBase, token),
        myBilling.requests(c.gwBase, token),
      ]);
      setSub(s);
      setPlans([...p].sort((a, b) => a.reqPerMonthQta - b.reqPerMonthQta));
      setReqs(r);
    } catch (err) {
      setError(friendlyError(err));
    }
  }, [c.auth, c.gwBase]);

  useEffect(() => {
    load();
  }, [load]);

  if (!sub) {
    return (
      <>
        <PageHeader title="Billing" />
        {error ? <ErrorBanner message={error} /> : <Spinner />}
      </>
    );
  }

  const waiting = reqs.find((r) => r.sts === "PENDING");
  const past = reqs.filter((r) => r.sts !== "PENDING");
  const latestDecided = past[0];

  return (
    <>
      <PageHeader
        title="Billing"
        subtitle="Your plan, and how much of this month's allowance you've used. Every API call counts, including this console's."
      />
      {error ? <ErrorBanner message={error} /> : null}
      {sub.dfSubSts === "SUSPENDED" ? (
        <div className="df-callout">
          <span>Your subscription is suspended, so new consent requests are refused. Contact the platform to reactivate it.</span>
        </div>
      ) : null}

      <div className="cm-summary">
        <div className="cm-stat">
          <b>{sub.subPlnName}</b>
          <span>Plan</span>
        </div>
        <div className="cm-stat">
          <b>
            <Badge status={sub.dfSubSts} />
          </b>
          <span>Subscription</span>
        </div>
        <div className="cm-stat" style={{ minWidth: 240 }}>
          <b>
            {sub.curPeriodUsg.toLocaleString()} / {sub.reqPerMonthQta.toLocaleString()}
          </b>
          <span>API calls this month</span>
          <UsageMeter used={sub.curPeriodUsg} of={sub.reqPerMonthQta} label="Share of this month's allowance used" />
        </div>
        <div className="cm-stat">
          <b>{sub.reqPerSecQta.toLocaleString()}</b>
          <span>API calls a second from your systems, at most</span>
        </div>
      </div>
      <p className="cm-note">{usageNote(sub)}</p>

      {waiting ? (
        <Card>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap", alignItems: "center" }}>
            <div>
              <strong>
                You asked for {waiting.toPlnName}
              </strong>
              <div className="cm-note" style={{ margin: 0 }}>
                {waiting.requestedByName ?? "Someone"} asked on {fmtDate(waiting.requestedAt)}. The platform will approve or
                decline it.
              </div>
            </div>
            {mayAsk ? (
              <Button
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try {
                    await myBilling.withdraw(c.gwBase, await freshToken(c.auth), waiting.id);
                    await load();
                  } catch (err) {
                    setError(friendlyError(err));
                  } finally {
                    setBusy(false);
                  }
                }}
              >
                Withdraw
              </Button>
            ) : null}
          </div>
        </Card>
      ) : latestDecided && latestDecided.sts === "DECLINED" ? (
        <div className="df-callout">
          <span>
            Your request for {latestDecided.toPlnName} was declined on {fmtDate(latestDecided.decidedAt)}: “{latestDecided.decisionNote}”
          </span>
        </div>
      ) : null}

      <h3 className="cm-section-title">Plans</h3>
      <div style={{ overflowX: "auto" }}>
        <table className="cm-table">
          <thead>
            <tr>
              <th>Plan</th>
              <th>API calls a month</th>
              <th>A second</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {plans.map((p) => (
              <tr key={p.id}>
                <td>
                  <strong>{p.name}</strong>
                </td>
                <td>{p.reqPerMonthQta.toLocaleString()}</td>
                <td>{p.reqPerSecQta.toLocaleString()}</td>
                <td>
                  {p.name === sub.subPlnName ? (
                    <span className="cm-badge cm-badge-active">Your plan</span>
                  ) : waiting ? null : mayAsk ? (
                    <AskForPlan console={c} plan={p} onAsked={load} />
                  ) : null}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!mayAsk ? <p className="cm-note">An Owner or Administrator asks for another plan.</p> : null}

      {past.length ? (
        <>
          <h3 className="cm-section-title">Earlier requests</h3>
          <div style={{ overflowX: "auto" }}>
            <table className="cm-table">
              <thead>
                <tr>
                  <th>Asked</th>
                  <th>Change</th>
                  <th>Outcome</th>
                  <th>Decided</th>
                  <th>Note</th>
                </tr>
              </thead>
              <tbody>
                {past.map((r) => (
                  <tr key={r.id}>
                    <td>
                      {fmtDate(r.requestedAt)}
                      <div className="cm-note" style={{ margin: 0 }}>
                        {r.requestedByName}
                      </div>
                    </td>
                    <td>
                      {r.fromPlnName} → {r.toPlnName}
                    </td>
                    <td>
                      <Badge status={r.sts} />
                    </td>
                    <td>{fmtDate(r.decidedAt)}</td>
                    <td>{r.decisionNote ?? r.note ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}
    </>
  );
}
