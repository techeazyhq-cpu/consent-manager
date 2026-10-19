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
import {
  ApiError,
  bil,
  cnsAdmin,
  dfReg,
  type AdminCnsReqView,
  type CnsLcySts,
  type CnsStats,
  type DfProfileRespDto,
  type DfSubStatusResult,
  type PlnChgReqDto,
  type SubPln,
} from "api-client";
import { Badge, Button, Card, ErrorBanner, Field, Spinner, UsageMeter, friendlyError } from "ui-kit/src/index";
import { AuditPanel } from "./Audit";
import { PendingRequest } from "./Billing";
import DfLogo, { forgetLogo } from "./DfLogo";
import { checkLogo } from "./logo";
import { TENANT_TABS, tenantHref, type TenantTab } from "./routes";
import { takeNotice, takeSecret } from "./secrets";
import { freshToken, type Session } from "./session";

const TAB_LABELS: Record<TenantTab, string> = {
  overview: "Overview",
  subscription: "Subscription",
  consents: "Consents",
  team: "Team",
  audit: "Audit",
};

const fmtDate = (iso?: string | null) => (iso ? new Date(iso).toLocaleString() : "—");

/** Everything about one tenant, in tabs: its organisation, subscription, consents and first Owner. */
export default function TenantDetail({ session, dfId, tab }: { session: Session; dfId: string; tab: TenantTab }) {
  const [df, setDf] = useState<DfProfileRespDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const [secret, setSecret] = useState<string | undefined>(() => takeSecret(dfId));
  const [notice] = useState<string | undefined>(() => takeNotice(dfId));
  const [logoVersion, setLogoVersion] = useState(0);

  const load = async () => {
    try {
      setDf(await dfReg.profile(session.gwBase, await freshToken(session.auth), dfId));
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) setNotFound(true);
      else setError(friendlyError(err));
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dfId]);

  if (notFound) {
    return (
      <>
        <a className="cm-back" href="#/dashboard">
          ← Dashboard
        </a>
        <p>There is no tenant {dfId}.</p>
      </>
    );
  }
  if (!df) {
    return error ? <ErrorBanner message={error} /> : <Spinner label="Loading tenant…" />;
  }

  return (
    <>
      <a className="cm-back" href="#/dashboard">
        ← Dashboard
      </a>
      <div className="cm-head">
        <DfLogo session={session} dfId={df.id} name={df.name} size={84} version={logoVersion} />
        <div style={{ minWidth: 0 }}>
          <h1>{df.name}</h1>
          <p>
            {[df.legalName, df.industry].filter(Boolean).join(" · ") || df.contactEmail}{" "}
            <Badge status={df.sts} />
          </p>
        </div>
      </div>

      {error ? <ErrorBanner message={error} /> : null}
      {notice ? <ErrorBanner message={notice} /> : null}
      {secret ? (
        <div className="cm-secret" role="alert">
          <strong>Copy the API client secret now: it isn't shown again.</strong> The fiduciary's systems sign in as
          client <code>df-api-{df.id}</code>
          with this secret:
          <code>{secret}</code>
          <Button style={{ marginTop: 8 }} onClick={() => setSecret(undefined)}>
            I've copied it
          </Button>
        </div>
      ) : null}

      <div className="cm-tabs" role="tablist" aria-label="Tenant">
        {TENANT_TABS.map((t) => (
          <button
            key={t}
            role="tab"
            aria-selected={tab === t}
            onClick={() => {
              window.location.hash = tenantHref(dfId, t);
            }}
          >
            {TAB_LABELS[t]}
          </button>
        ))}
      </div>

      {tab === "overview" ? (
        <Overview
          session={session}
          df={df}
          onActivated={(s) => {
            if (s) setSecret(s);
            load();
          }}
          onLogo={() => {
            forgetLogo(df.id);
            setLogoVersion((v) => v + 1);
          }}
        />
      ) : null}
      {tab === "subscription" ? <Subscription session={session} df={df} /> : null}
      {tab === "consents" ? <Consents session={session} df={df} /> : null}
      {tab === "team" ? <Team session={session} df={df} /> : null}
      {tab === "audit" ? <AuditPanel session={session} dfId={df.id} /> : null}
    </>
  );
}

function Overview({
  session,
  df,
  onActivated,
  onLogo,
}: {
  session: Session;
  df: DfProfileRespDto;
  onActivated: (secret?: string) => void;
  onLogo: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const fields: [string, string | null | undefined][] = [
    ["Tenant ID", df.id],
    ["Display name", df.name],
    ["Legal name", df.legalName],
    ["Industry", df.industry],
    ["Contact email", df.contactEmail],
    ["Phone", df.contactPhone],
    ["Website", df.website],
    ["Registered address", df.registeredAddress],
    ["CIN", df.cin],
    ["GSTIN", df.gstin],
    ["Data Protection Officer", [df.dpoName, df.dpoEmail].filter(Boolean).join(" · ")],
    ["Plan chosen at onboarding", df.subPlnName ?? "FREE"],
    ["Registered", fmtDate(df.registeredAt)],
    ["Activated", fmtDate(df.activatedAt)],
  ];

  return (
    <>
      {error ? <ErrorBanner message={error} /> : null}
      {df.sts === "PENDING" ? (
        <Card style={{ marginBottom: 16 }}>
          <strong>Awaiting activation.</strong>
          <p className="cm-note">
            Activating creates the fiduciary's API client, starts its subscription and invites {df.contactEmail} as its
            Owner.
          </p>
          <Button
            variant="primary"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                const resp = await dfReg.activate(session.gwBase, await freshToken(session.auth), df.id);
                onActivated(resp.cmDfApiClientSecret ?? undefined);
              } catch (err) {
                setError(friendlyError(err));
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Activating…" : "Activate"}
          </Button>
        </Card>
      ) : null}

      <Card>
        <dl className="cm-fields">
          {fields.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd>{value || "—"}</dd>
            </div>
          ))}
        </dl>
      </Card>

      <h3 className="cm-section-title">Logo</h3>
      <Card>
        <Field label="Replace the logo (PNG or JPEG, up to 256 KB)">
          <input
            type="file"
            accept="image/png,image/jpeg"
            disabled={busy}
            onChange={async (e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (!file) return;
              setDone(null);
              const checked = await checkLogo(file);
              if ("problem" in checked) {
                setError(checked.problem);
                return;
              }
              setBusy(true);
              setError(null);
              try {
                await dfReg.uploadLogo(session.gwBase, await freshToken(session.auth), df.id, file);
                setDone("Logo updated.");
                onLogo();
              } catch (err) {
                setError(friendlyError(err));
              } finally {
                setBusy(false);
              }
            }}
          />
        </Field>
        {done ? <span className="cm-ok">{done}</span> : null}
      </Card>
    </>
  );
}

function Subscription({ session, df }: { session: Session; df: DfProfileRespDto }) {
  const [status, setStatus] = useState<DfSubStatusResult | null>(null);
  const [plans, setPlans] = useState<SubPln[]>([]);
  const [waiting, setWaiting] = useState<PlnChgReqDto | null>(null);
  const [plan, setPlan] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = async (attempt = 0) => {
    try {
      const token = await freshToken(session.auth);
      const [s, p, pending] = await Promise.all([
        bil.subStatus(session.gwBase, token, df.id),
        bil.listSubPlns(session.gwBase, token),
        bil.requests(session.gwBase, token, "PENDING"),
      ]);
      setWaiting(pending.find((r) => r.dfId === df.id) ?? null);
      setStatus(s);
      setPlan(s.subPlnName);
      setPlans(p);
    } catch (err) {
      // the subscription is created from the activation event, a moment after activation
      if (err instanceof ApiError && err.status === 404 && attempt < 5) {
        setTimeout(() => load(attempt + 1), 800);
        return;
      }
      setError(friendlyError(err));
    }
  };

  useEffect(() => {
    if (df.sts === "ACTIVE") load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [df.sts]);

  if (df.sts !== "ACTIVE") return <p className="cm-note">The subscription starts when the tenant is activated.</p>;
  if (!status) return error ? <ErrorBanner message={error} /> : <Spinner label="Loading subscription…" />;

  const run = async (action: (token: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action(await freshToken(session.auth));
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      {error ? <ErrorBanner message={error} /> : null}
      <div className="cm-summary">
        <div className="cm-stat">
          <b>{status.subPlnName}</b>
          <span>Plan</span>
        </div>
        <div className="cm-stat">
          <b>
            <Badge status={status.dfSubSts} />
          </b>
          <span>Subscription</span>
        </div>
        <div className="cm-stat">
          <b>
            {status.curPeriodUsg.toLocaleString()} / {status.reqPerMonthQta.toLocaleString()}
          </b>
          <span>API calls this month</span>
          <UsageMeter used={status.curPeriodUsg} of={status.reqPerMonthQta} label="Share of the monthly API allowance used" />
        </div>
      </div>

      {waiting ? (
        <>
          <h3 className="cm-section-title">Plan change asked for</h3>
          <PendingRequest session={session} req={waiting} onDecided={() => load()} />
        </>
      ) : null}

      {session.isAdmin ? (
        <Card>
          <div style={{ display: "flex", gap: 12, alignItems: "flex-end", flexWrap: "wrap" }}>
            <Field label="Change plan">
              <select value={plan} onChange={(e) => setPlan(e.target.value)}>
                {plans.map((p) => (
                  <option key={p.id} value={p.name}>
                    {p.name} — {p.reqPerMonthQta.toLocaleString()} API calls a month
                  </option>
                ))}
              </select>
            </Field>
            <Button
              disabled={busy || plan === status.subPlnName}
              onClick={() => run((token) => bil.assignSubPln(session.gwBase, token, df.id, plan))}
            >
              Change plan
            </Button>
            <div style={{ marginLeft: "auto" }}>
              <Button
                variant={status.dfSubSts === "ACTIVE" ? "danger" : "primary"}
                disabled={busy}
                onClick={() => {
                  const suspend = status.dfSubSts === "ACTIVE";
                  if (suspend && !window.confirm(`Suspend ${df.name}? It can't raise consent requests until reactivated.`)) {
                    return;
                  }
                  run((token) => bil.setSubSts(session.gwBase, token, df.id, !suspend));
                }}
              >
                {status.dfSubSts === "ACTIVE" ? "Suspend" : "Reactivate"}
              </Button>
            </div>
          </div>
        </Card>
      ) : (
        <p className="cm-note">Only platform administrators change a tenant's plan or suspend it.</p>
      )}
    </>
  );
}

const STATUS_LABELS: { key: keyof CnsStats; sts?: CnsLcySts; label: string }[] = [
  { key: "sent", label: "Sent" },
  { key: "pending", sts: "PENDING", label: "Awaiting the principal" },
  { key: "granted", sts: "ISSUED", label: "Granted" },
  { key: "denied", sts: "DENIED", label: "Denied" },
  { key: "revoked", sts: "REVOKED", label: "Withdrawn" },
  { key: "expired", sts: "EXPIRED", label: "Expired" },
  { key: "notificationFailed", sts: "NOTIFICATION_FAILED", label: "Couldn't notify" },
];

const PAGE = 25;

function Consents({ session, df }: { session: Session; df: DfProfileRespDto }) {
  const [stats, setStats] = useState<CnsStats | null>(null);
  const [rows, setRows] = useState<AdminCnsReqView[] | null>(null);
  const [sts, setSts] = useState<CnsLcySts | undefined>();
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    freshToken(session.auth)
      .then((token) => cnsAdmin.stats(session.gwBase, token, df.id))
      .then(setStats)
      .catch((err) => setError(friendlyError(err)));
  }, [session, df.id]);

  useEffect(() => {
    setRows(null);
    freshToken(session.auth)
      .then((token) => cnsAdmin.requests(session.gwBase, token, df.id, { sts, limit: PAGE, offset }))
      .then(setRows)
      .catch((err) => {
        setError(friendlyError(err));
        setRows([]);
      });
  }, [session, df.id, sts, offset]);

  return (
    <>
      {error ? <ErrorBanner message={error} /> : null}
      <div className="cm-summary">
        {STATUS_LABELS.map(({ key, sts: s, label }) => (
          <button
            key={key}
            className="cm-stat"
            style={{
              textAlign: "left",
              cursor: "pointer",
              color: "inherit",
              font: "inherit",
              borderColor: sts === s ? "var(--cm-primary)" : undefined,
            }}
            aria-pressed={sts === s}
            onClick={() => {
              setSts(s);
              setOffset(0);
            }}
          >
            <b>{stats ? (stats[key] as number).toLocaleString() : "–"}</b>
            <span>{label}</span>
          </button>
        ))}
      </div>

      <p className="cm-note">
        Data principals are shown only as pseudonyms: the platform runs consent, it doesn't look at the people behind
        it.
      </p>

      {rows === null ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <p className="cm-note">{offset === 0 ? "No consent requests here yet." : "No more requests."}</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Data principal</th>
                <th>Purpose</th>
                <th>Data categories</th>
                <th>Status</th>
                <th>Valid until</th>
                <th>Raised</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.cnsReqId}>
                  <td>
                    <code>{r.dpPseudonym}</code>
                  </td>
                  <td>{r.prpsCode}</td>
                  <td>{r.dCatCodes.join(", ")}</td>
                  <td>
                    <Badge status={r.lcySts} />
                  </td>
                  <td>{fmtDate(r.validUntil)}</td>
                  <td>{fmtDate(r.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        <Button disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE))}>
          Previous
        </Button>
        <Button disabled={!rows || rows.length < PAGE} onClick={() => setOffset(offset + PAGE)}>
          Next
        </Button>
      </div>
    </>
  );
}

function Team({ session, df }: { session: Session; df: DfProfileRespDto }) {
  const [form, setForm] = useState({ email: df.contactEmail, name: df.legalName ?? df.name });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  return (
    <>
      <p className="cm-note" style={{ marginTop: 0 }}>
        A fiduciary manages its own people in the DF Console. The platform only helps when activation couldn't invite
        its first Owner, for example because that address was already in use.
      </p>
      {error ? <ErrorBanner message={error} /> : null}
      {done ? <p className="cm-ok">{done}</p> : null}
      {df.sts !== "ACTIVE" ? (
        <p className="cm-note">Activate the tenant first; activation invites {df.contactEmail} as its Owner.</p>
      ) : (
        <Card>
          <h3 style={{ marginTop: 0 }}>Invite the first Owner</h3>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              setError(null);
              setDone(null);
              try {
                const owner = await dfReg.inviteOwner(session.gwBase, await freshToken(session.auth), df.id, {
                  email: form.email.trim(),
                  name: form.name.trim(),
                });
                setDone(`${owner.email} is now the Owner and can sign in to the DF Console.`);
              } catch (err) {
                setError(friendlyError(err));
              } finally {
                setBusy(false);
              }
            }}
          >
            <div className="cm-form-grid">
              <Field label="Email">
                <input type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
              </Field>
              <Field label="Name">
                <input required maxLength={100} value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </Field>
            </div>
            <Button type="submit" variant="primary" disabled={busy}>
              Invite as Owner
            </Button>
          </form>
        </Card>
      )}
    </>
  );
}
