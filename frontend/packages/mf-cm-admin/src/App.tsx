import { Fragment, useEffect, useState } from "react";
import { ApiError, bil, dfReg, type AuthInfo, type DfListItemRespDto, type DfSubStatusResult, type SubPln } from "api-client";
import { Badge, Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";

interface Props {
  auth: AuthInfo;
  gwBase: string;
}

export default function CmAdminApp({ auth, gwBase }: Props) {
  const [dfs, setDfs] = useState<DfListItemRespDto[]>([]);
  const [subPlns, setSubPlns] = useState<SubPln[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [revealedSecrets, setRevealedSecrets] = useState<Record<string, string>>({});
  const [newDf, setNewDf] = useState({ name: "", contactEmail: "" });
  const [registering, setRegistering] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [dfList, plns] = await Promise.all([dfReg.list(gwBase, auth.token), bil.listSubPlns(gwBase, auth.token)]);
      setDfs(dfList);
      setSubPlns(plns);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <>
      <div className="cm-topbar">
        <strong>CM Admin</strong>
        <button onClick={auth.logout}>Log out</button>
      </div>
    <div className="cm-page">
      <PageHeader title="CM Admin" subtitle="Onboard Data Fiduciaries and manage their subscriptions." />
      {error ? <ErrorBanner message={error} /> : null}

      <Card>
        <h3 style={{ marginTop: 0 }}>Register a new Data Fiduciary</h3>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0 16px" }}>
          <Field label="Organization name">
            <input value={newDf.name} onChange={(e) => setNewDf({ ...newDf, name: e.target.value })} />
          </Field>
          <Field label="Contact email">
            <input value={newDf.contactEmail} onChange={(e) => setNewDf({ ...newDf, contactEmail: e.target.value })} />
          </Field>
        </div>
        <Button
          variant="primary"
          disabled={registering || !newDf.name || !newDf.contactEmail}
          onClick={async () => {
            setRegistering(true);
            setError(null);
            try {
              await dfReg.register(gwBase, auth.token, newDf);
              setNewDf({ name: "", contactEmail: "" });
              await load();
            } catch (err) {
              setError(friendlyError(err));
            } finally {
              setRegistering(false);
            }
          }}
        >
          Register
        </Button>
      </Card>

      <div style={{ height: 20 }} />
      <h3>Data Fiduciaries</h3>
      {loading ? (
        <Spinner />
      ) : (
        <table className="cm-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>DF ID</th>
              <th>Contact</th>
              <th>Status</th>
              <th>Registered</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {dfs.map((df) => (
              <Fragment key={df.id}>
                <tr>
                  <td>{df.name}</td>
                  <td>
                    <code style={{ fontSize: 12, color: "var(--cm-text-dim)" }}>{df.id}</code>
                  </td>
                  <td>{df.contactEmail}</td>
                  <td>
                    <Badge status={df.sts} />
                  </td>
                  <td>{new Date(df.registeredAt).toLocaleDateString()}</td>
                  <td>
                    <Button onClick={() => setExpandedId(expandedId === df.id ? null : df.id)}>
                      {expandedId === df.id ? "Close" : "Manage"}
                    </Button>
                  </td>
                </tr>
                {expandedId === df.id ? (
                  <tr>
                    <td colSpan={6}>
                      <DfDetail
                        gwBase={gwBase}
                        auth={auth}
                        df={df}
                        subPlns={subPlns}
                        revealedSecret={revealedSecrets[df.id]}
                        onActivated={(secret) => {
                          setRevealedSecrets((prev) => ({ ...prev, [df.id]: secret }));
                          load();
                        }}
                        onChanged={load}
                      />
                    </td>
                  </tr>
                ) : null}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </div>
    </>
  );
}

function DfDetail({
  gwBase,
  auth,
  df,
  subPlns,
  revealedSecret,
  onActivated,
  onChanged,
}: {
  gwBase: string;
  auth: AuthInfo;
  df: DfListItemRespDto;
  subPlns: SubPln[];
  revealedSecret?: string;
  onActivated: (secret: string) => void;
  onChanged: () => void;
}) {
  const [status, setStatus] = useState<DfSubStatusResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [selectedPlan, setSelectedPlan] = useState("");

  const loadStatus = async (attempt = 0) => {
    if (df.sts !== "ACTIVE") return;
    try {
      const s = await bil.subStatus(gwBase, auth.token, df.id);
      setStatus(s);
      setSelectedPlan(s.subPlnName);
    } catch (err) {
      // Right after activation, cm-bil-svc's FREE-tier DfSub is created asynchronously (via
      // its Pulsar consumer reacting to DfActivatedEvt) — calling sub-status immediately can
      // land in the gap before that row exists, surfacing as a 404 "df-sub-not-found" even
      // though activation itself succeeded. Retry a few times before treating it as a real
      // error, rather than leaving the admin looking at a stuck failure for a timing quirk.
      if (err instanceof ApiError && err.status === 404 && attempt < 5) {
        setTimeout(() => loadStatus(attempt + 1), 800);
        return;
      }
      setError(friendlyError(err));
    }
  };

  useEffect(() => {
    loadStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [df.sts]);

  return (
    <Card style={{ margin: "8px 0" }}>
      {error ? <ErrorBanner message={error} /> : null}
      {revealedSecret ? (
        <ErrorBanner
          message={`DF ID: ${df.id}  |  Client secret (shown once — copy both now): ${revealedSecret}`}
        />
      ) : null}

      {df.sts === "PENDING" ? (
        <Button
          variant="primary"
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              const resp = await dfReg.activate(gwBase, auth.token, df.id);
              if (resp.cmDfApiClientSecret) onActivated(resp.cmDfApiClientSecret);
            } catch (err) {
              setError(friendlyError(err));
            } finally {
              setBusy(false);
            }
          }}
        >
          Activate
        </Button>
      ) : status ? (
        <div style={{ display: "flex", gap: 24, alignItems: "flex-end", flexWrap: "wrap" }}>
          <div>
            <div style={{ fontSize: 12, color: "var(--cm-text-dim)" }}>Subscription status</div>
            <Badge status={status.dfSubSts} />
          </div>
          <div>
            <div style={{ fontSize: 12, color: "var(--cm-text-dim)" }}>Usage this period</div>
            <div>
              {status.curPeriodUsg} / {status.reqPerMonthQta}
            </div>
          </div>
          <Field label="Plan">
            <select value={selectedPlan} onChange={(e) => setSelectedPlan(e.target.value)}>
              {subPlns.map((p) => (
                <option key={p.id} value={p.name}>
                  {p.name}
                </option>
              ))}
            </select>
          </Field>
          <Button
            disabled={busy || selectedPlan === status.subPlnName}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                await bil.assignSubPln(gwBase, auth.token, df.id, selectedPlan);
                await loadStatus();
                onChanged();
              } catch (err) {
                setError(friendlyError(err));
              } finally {
                setBusy(false);
              }
            }}
          >
            Assign plan
          </Button>
          <Button
            variant={status.dfSubSts === "ACTIVE" ? "danger" : "primary"}
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              setError(null);
              try {
                await bil.setSubSts(gwBase, auth.token, df.id, status.dfSubSts !== "ACTIVE");
                await loadStatus();
                onChanged();
              } catch (err) {
                setError(friendlyError(err));
              } finally {
                setBusy(false);
              }
            }}
          >
            {status.dfSubSts === "ACTIVE" ? "Suspend" : "Reactivate"}
          </Button>
        </div>
      ) : (
        <Spinner />
      )}
    </Card>
  );
}
