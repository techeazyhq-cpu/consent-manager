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

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { cnsCmd, cnsQry, dfReg, friendlyError, type CnsArtRdMdl, type GrantChoice } from "api-client";
import { awaitsDecision, endingSoon, statusOrder, type Change, type Decision } from "dp-kit";
import { GW_BASE } from "./config";
import { useAuth } from "./auth";

export interface Consents {
  rows: CnsArtRdMdl[] | null;
  error: string | null;
  clearError: () => void;
  /** The consent being changed, so its screen can show it. */
  busy: string | null;
  refreshing: boolean;
  reload: () => Promise<void>;
  orgName: (dfId: string) => string;
  byReqId: (cnsReqId: string) => CnsArtRdMdl | undefined;
  awaiting: CnsArtRdMdl[];
  endingSoon: CnsArtRdMdl[];
  /** Everything else, awaiting first, then in force, then the rest; newest first within each. */
  sorted: CnsArtRdMdl[];
  decide: (row: CnsArtRdMdl, decision: Decision, choice?: GrantChoice) => Promise<boolean>;
  change: (row: CnsArtRdMdl, change: Change) => Promise<boolean>;
}

const ConsentsContext = createContext<Consents | null>(null);

export function useConsents(): Consents {
  const c = useContext(ConsentsContext);
  if (!c) throw new Error("useConsents outside ConsentsProvider");
  return c;
}

/**
 * The person's consents and the organisations behind them, as the web portal has them (ADR-032 §3). A decision or a
 * change is recorded at once; the list is a read model updated from the event that follows, so it is re-read
 * briefly until it shows.
 */
export function ConsentsProvider({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const [rows, setRows] = useState<CnsArtRdMdl[] | null>(null);
  const [names, setNames] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const known = useRef(new Set<string>());

  const fetchRows = useCallback(async () => {
    const token = await auth.token();
    const data = await cnsQry.listMine(GW_BASE, token);
    setRows(data);
    const missing = Array.from(new Set(data.map((r) => r.dfId))).filter((id) => !known.current.has(id));
    missing.forEach((id) => known.current.add(id));
    if (missing.length) {
      const resolved = await Promise.all(
        missing.map((id) =>
          dfReg
            .getOne(GW_BASE, token, id)
            .then((d) => [id, d.name] as const)
            .catch(() => [id, "An organisation"] as const),
        ),
      );
      setNames((prev) => ({ ...prev, ...Object.fromEntries(resolved) }));
    }
    return data;
  }, [auth]);

  const reload = useCallback(async () => {
    setError(null);
    setRefreshing(true);
    try {
      // requests sent to the person's email before they had an account are claimed on every load; repeating is harmless
      const email = auth.person?.email;
      if (email) {
        const token = await auth.token();
        await cnsCmd.linkDp(GW_BASE, token, { extDpRefTyp: "EMAIL", extDpRefVal: email }).catch(() => undefined);
      }
      await fetchRows();
    } catch (err) {
      setError(friendlyError(err));
      setRows((prev) => prev ?? []);
    } finally {
      setRefreshing(false);
    }
  }, [auth, fetchRows]);

  useEffect(() => {
    if (auth.state === "signed-in") reload();
    else setRows(null);
    // only on signing in or out, not on each token refresh
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [auth.state]);

  const settle = useCallback(
    async (row: CnsArtRdMdl, changed: (now: CnsArtRdMdl | undefined) => boolean) => {
      for (let attempt = 0; attempt < 8; attempt++) {
        const data = await fetchRows();
        if (changed(data.find((r) => r.cnsReqId === row.cnsReqId))) return;
        await new Promise((r) => setTimeout(r, 600));
      }
    },
    [fetchRows],
  );

  const decide = useCallback(
    async (row: CnsArtRdMdl, decision: Decision, choice?: GrantChoice) => {
      setBusy(row.cnsArtId ?? row.cnsReqId);
      setError(null);
      try {
        const token = await auth.token();
        if (decision === "give") await cnsCmd.approve(GW_BASE, token, row.cnsReqId, choice);
        else if (decision === "decline") await cnsCmd.deny(GW_BASE, token, row.cnsReqId);
        else if (row.cnsArtId) await cnsCmd.revoke(GW_BASE, token, row.cnsArtId);
        await settle(row, (now) => now?.lcySts !== row.lcySts);
        return true;
      } catch (err) {
        setError(friendlyError(err));
        return false;
      } finally {
        setBusy(null);
      }
    },
    [auth, settle],
  );

  const change = useCallback(
    async (row: CnsArtRdMdl, ch: Change) => {
      if (!row.cnsArtId) return false;
      setBusy(row.cnsArtId);
      setError(null);
      try {
        const token = await auth.token();
        if (ch.kind === "shorten") await cnsCmd.shorten(GW_BASE, token, row.cnsArtId, ch.until);
        else if (ch.kind === "withdraw-dcat") await cnsCmd.withdrawDCat(GW_BASE, token, row.cnsArtId, ch.dCat);
        else await cnsCmd.renew(GW_BASE, token, row.cnsArtId, ch.days);
        await settle(row, (now) => now?.updatedAt !== row.updatedAt);
        return true;
      } catch (err) {
        setError(friendlyError(err));
        return false;
      } finally {
        setBusy(null);
      }
    },
    [auth, settle],
  );

  const value = useMemo<Consents>(() => {
    const all = rows ?? [];
    return {
      rows,
      error,
      clearError: () => setError(null),
      busy,
      refreshing,
      reload,
      orgName: (dfId) => names[dfId] ?? "…",
      byReqId: (id) => all.find((r) => r.cnsReqId === id),
      awaiting: all.filter((r) => awaitsDecision(r.lcySts)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
      endingSoon: endingSoon(all),
      sorted: [...all].sort(
        (a, b) => statusOrder(a.lcySts) - statusOrder(b.lcySts) || b.updatedAt.localeCompare(a.updatedAt),
      ),
      decide,
      change,
    };
  }, [rows, error, busy, refreshing, reload, names, decide, change]);

  return <ConsentsContext.Provider value={value}>{children}</ConsentsContext.Provider>;
}
