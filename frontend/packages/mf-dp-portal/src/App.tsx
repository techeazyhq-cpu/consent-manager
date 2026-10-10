import { useEffect, useMemo, useState } from "react";
import { cnsCmd, cnsQry, dfReg, type AuthInfo, type CnsArtRdMdl } from "api-client";
import { Badge, Button, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import PushToggle, { forgetThisBrowser } from "./PushToggle";
import { onForegroundPush } from "./push";

interface Props {
  auth: AuthInfo;
  gwBase: string;
}

interface Tile {
  dfId: string;
  dfName: string;
  activeCnt: number;
  pendingCnt: number;
  lastUpdatedAt: string;
}

export default function DpPortalApp({ auth, gwBase }: Props) {
  const [rows, setRows] = useState<CnsArtRdMdl[]>([]);
  const [names, setNames] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [selectedDfId, setSelectedDfId] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      // Auto-link (plan §7a): a DF can raise a request for a DP by email/phone before that
      // person has a CM identity at all — it stays an UnlnkDpRef until something calls
      // dp-links. Nothing else in this app ever calls it, so without this, a DP could log in
      // and never see a DF's already-raised request. Safe to call on every load: the backend
      // treats repeat linking of the same (typ, val, dpId) as a no-op.
      if (auth.email) {
        await cnsCmd.linkDp(gwBase, auth.token, { extDpRefTyp: "EMAIL", extDpRefVal: auth.email }).catch(() => {});
      }
      const data = await cnsQry.listMine(gwBase, auth.token);
      setRows(data);
      const uniqueDfIds = Array.from(new Set(data.map((r) => r.dfId))).filter((id) => !(id in names));
      if (uniqueDfIds.length > 0) {
        const resolved = await Promise.all(
          uniqueDfIds.map((id) =>
            dfReg
              .getOne(gwBase, auth.token, id)
              .then((d) => [id, d.name] as const)
              .catch(() => [id, id] as const),
          ),
        );
        setNames((prev) => ({ ...prev, ...Object.fromEntries(resolved) }));
      }
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

  // A push that arrives while the portal is open is shown here rather than as a system notification.
  useEffect(() => {
    let unsubscribe = () => {};
    onForegroundPush((payload) => {
      setNotice(payload.notification?.body ?? payload.data?.body ?? "New consent request");
      load();
    }).then((fn) => (unsubscribe = fn));
    return () => unsubscribe();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Signing out also stops this browser getting the DP's notifications (it may be a shared computer).
  const logout = async () => {
    await forgetThisBrowser(auth, gwBase).catch(() => {});
    auth.logout();
  };

  const tiles = useMemo<Tile[]>(() => {
    const byDf = new Map<string, Tile>();
    for (const row of rows) {
      const existing = byDf.get(row.dfId) ?? {
        dfId: row.dfId,
        dfName: names[row.dfId] ?? row.dfId,
        activeCnt: 0,
        pendingCnt: 0,
        lastUpdatedAt: row.updatedAt,
      };
      if (row.lcySts === "ISSUED") existing.activeCnt += 1;
      if (row.lcySts === "PENDING") existing.pendingCnt += 1;
      if (row.updatedAt > existing.lastUpdatedAt) existing.lastUpdatedAt = row.updatedAt;
      existing.dfName = names[row.dfId] ?? row.dfId;
      byDf.set(row.dfId, existing);
    }
    return Array.from(byDf.values()).sort((a, b) => (a.lastUpdatedAt < b.lastUpdatedAt ? 1 : -1));
  }, [rows, names]);

  const selectedRows = selectedDfId ? rows.filter((r) => r.dfId === selectedDfId) : [];

  // Approve/deny/revoke write synchronously on cm-cns-cmd-svc but the read model here only
  // reflects it once cm-cns-qry-svc's Pulsar projector has processed the resulting event —
  // a single load() right after the action can still show the pre-action status. Poll
  // briefly for the row's status to actually change rather than assume one refresh landed.
  const act = async (fn: () => Promise<unknown>, key: string, cnsReqId: string, oldStatus: string) => {
    setBusyId(key);
    setError(null);
    try {
      await fn();
      for (let attempt = 0; attempt < 6; attempt++) {
        const data = await cnsQry.listMine(gwBase, auth.token);
        setRows(data);
        const stillOld = data.find((r) => r.cnsReqId === cnsReqId)?.lcySts === oldStatus;
        if (!stillOld) break;
        await new Promise((r) => setTimeout(r, 700));
      }
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <>
      <div className="cm-topbar">
        <strong>DP Portal</strong>
        <div className="cm-topbar-actions">
          <PushToggle auth={auth} gwBase={gwBase} onError={setError} />
          <button onClick={logout}>Log out</button>
        </div>
      </div>
    <div className="cm-page">
      <PageHeader title="My consents" subtitle="One tile per organization you've shared consent with." />
      {error ? <ErrorBanner message={error} /> : null}
      {notice ? (
        <div className="cm-notice-banner" role="status">
          New consent request: {notice}
        </div>
      ) : null}

      {loading ? (
        <Spinner />
      ) : tiles.length === 0 ? (
        <p style={{ color: "var(--cm-text-dim)" }}>No consent activity yet.</p>
      ) : (
        <div className="cm-tile-grid">
          {tiles.map((tile) => (
            <div
              key={tile.dfId}
              className={`cm-tile ${selectedDfId === tile.dfId ? "cm-tile-active" : ""}`}
              onClick={() => setSelectedDfId(tile.dfId === selectedDfId ? null : tile.dfId)}
            >
              <div style={{ fontWeight: 600, marginBottom: 8 }}>{tile.dfName}</div>
              <div style={{ display: "flex", gap: 14, fontSize: 13, color: "var(--cm-text-dim)" }}>
                <span>{tile.activeCnt} active</span>
                <span>{tile.pendingCnt} pending</span>
              </div>
            </div>
          ))}
        </div>
      )}

      {selectedDfId ? (
        <div style={{ marginTop: 24 }}>
          <h3>{names[selectedDfId] ?? selectedDfId}</h3>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Purpose</th>
                <th>Data categories</th>
                <th>Status</th>
                <th>Valid until</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {selectedRows.map((row) => (
                <tr key={row.cnsReqId}>
                  <td>{row.prpsCode}</td>
                  <td>{row.dCatCodes.join(", ")}</td>
                  <td>
                    <Badge status={row.lcySts} />
                  </td>
                  <td>{row.validUntil ? new Date(row.validUntil).toLocaleDateString() : "—"}</td>
                  <td style={{ display: "flex", gap: 6 }}>
                    {row.lcySts === "PENDING" ? (
                      <>
                        <Button
                          variant="primary"
                          disabled={busyId === row.cnsReqId}
                          onClick={() =>
                            act(() => cnsCmd.approve(gwBase, auth.token, row.cnsReqId), row.cnsReqId, row.cnsReqId, "PENDING")
                          }
                        >
                          Approve
                        </Button>
                        <Button
                          variant="danger"
                          disabled={busyId === row.cnsReqId}
                          onClick={() =>
                            act(() => cnsCmd.deny(gwBase, auth.token, row.cnsReqId), row.cnsReqId, row.cnsReqId, "PENDING")
                          }
                        >
                          Deny
                        </Button>
                      </>
                    ) : null}
                    {row.lcySts === "ISSUED" && row.cnsArtId ? (
                      <Button
                        variant="danger"
                        disabled={busyId === row.cnsArtId}
                        onClick={() =>
                          act(
                            () => cnsCmd.revoke(gwBase, auth.token, row.cnsArtId as string),
                            row.cnsArtId as string,
                            row.cnsReqId,
                            "ISSUED",
                          )
                        }
                      >
                        Revoke
                      </Button>
                    ) : null}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : null}
    </div>
    </>
  );
}
