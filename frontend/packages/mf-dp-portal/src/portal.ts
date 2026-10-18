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

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ensureFreshToken, getKeycloak, type AuthInfo } from "auth-kit";
import { cnsCmd, cnsQry, dfReg, type CnsArtRdMdl, type CnsLcySts } from "api-client";
import { friendlyError } from "ui-kit/src/index";
import { awaitsDecision } from "./labels";

/** A token valid for at least the next 30 seconds, refreshed if need be. */
export async function freshToken(auth: AuthInfo): Promise<string> {
  const kc = getKeycloak();
  if (!kc) return auth.token;
  await ensureFreshToken(kc);
  return kc.token ?? auth.token;
}

/** One organisation the person has consent activity with, for its tile. */
export interface Org {
  dfId: string;
  name: string;
  given: number;
  awaiting: number;
  lastUpdatedAt: string;
}

export type Decision = "give" | "decline" | "withdraw";

export interface Portal {
  auth: AuthInfo;
  gwBase: string;
  rows: CnsArtRdMdl[] | null;
  names: Record<string, string>;
  orgs: Org[];
  awaiting: CnsArtRdMdl[];
  error: string | null;
  setError: (message: string | null) => void;
  /** The consent record being changed, so its buttons can show it. */
  busy: string | null;
  reload: () => Promise<void>;
  decide: (row: CnsArtRdMdl, decision: Decision) => Promise<boolean>;
  orgName: (dfId: string) => string;
}

/** Everything the portal's pages share: the person's consents, the organisations behind them, and the actions. */
export function usePortal(auth: AuthInfo, gwBase: string): Portal {
  const [rows, setRows] = useState<CnsArtRdMdl[] | null>(null);
  const [names, setNames] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const known = useRef(new Set<string>());

  const resolveNames = useCallback(
    async (data: CnsArtRdMdl[], token: string) => {
      const missing = Array.from(new Set(data.map((r) => r.dfId))).filter((id) => !known.current.has(id));
      missing.forEach((id) => known.current.add(id));
      if (missing.length === 0) return;
      const resolved = await Promise.all(
        missing.map((id) =>
          dfReg
            .getOne(gwBase, token, id)
            .then((d) => [id, d.name] as const)
            .catch(() => [id, "An organisation"] as const),
        ),
      );
      setNames((prev) => ({ ...prev, ...Object.fromEntries(resolved) }));
    },
    [gwBase],
  );

  const fetchRows = useCallback(async () => {
    const token = await freshToken(auth);
    const data = await cnsQry.listMine(gwBase, token);
    setRows(data);
    await resolveNames(data, token);
    return data;
  }, [auth, gwBase, resolveNames]);

  const reload = useCallback(async () => {
    setError(null);
    try {
      // An organisation can ask a person for consent by email before they have an account here; linking claims
      // those requests. Repeating it is harmless, so it runs on every load.
      if (auth.email) {
        const token = await freshToken(auth);
        await cnsCmd.linkDp(gwBase, token, { extDpRefTyp: "EMAIL", extDpRefVal: auth.email }).catch(() => {});
      }
      await fetchRows();
    } catch (err) {
      setError(friendlyError(err));
      setRows((prev) => prev ?? []);
    }
  }, [auth, gwBase, fetchRows]);

  useEffect(() => {
    reload();
  }, [reload]);

  /**
   * Gives, declines or withdraws. The decision is recorded at once, but the list here is a read model updated from
   * the event that follows, so it is re-read briefly until the record shows its new status.
   */
  const decide = useCallback(
    async (row: CnsArtRdMdl, decision: Decision): Promise<boolean> => {
      const key = row.cnsArtId ?? row.cnsReqId;
      setBusy(key);
      setError(null);
      const before: CnsLcySts = row.lcySts;
      try {
        const token = await freshToken(auth);
        if (decision === "give") await cnsCmd.approve(gwBase, token, row.cnsReqId);
        else if (decision === "decline") await cnsCmd.deny(gwBase, token, row.cnsReqId);
        else if (row.cnsArtId) await cnsCmd.revoke(gwBase, token, row.cnsArtId);
        for (let attempt = 0; attempt < 8; attempt++) {
          const data = await fetchRows();
          if (data.find((r) => r.cnsReqId === row.cnsReqId)?.lcySts !== before) break;
          await new Promise((r) => setTimeout(r, 600));
        }
        return true;
      } catch (err) {
        setError(friendlyError(err));
        return false;
      } finally {
        setBusy(null);
      }
    },
    [auth, gwBase, fetchRows],
  );

  const orgName = useCallback((dfId: string) => names[dfId] ?? "…", [names]);

  const orgs = useMemo<Org[]>(() => {
    const byDf = new Map<string, Org>();
    for (const row of rows ?? []) {
      const org = byDf.get(row.dfId) ?? { dfId: row.dfId, name: orgName(row.dfId), given: 0, awaiting: 0, lastUpdatedAt: row.updatedAt };
      if (row.lcySts === "ISSUED") org.given += 1;
      if (awaitsDecision(row.lcySts)) org.awaiting += 1;
      if (row.updatedAt > org.lastUpdatedAt) org.lastUpdatedAt = row.updatedAt;
      byDf.set(row.dfId, org);
    }
    // organisations waiting on the person first, then the most recently active
    return Array.from(byDf.values()).sort(
      (a, b) => Number(b.awaiting > 0) - Number(a.awaiting > 0) || b.lastUpdatedAt.localeCompare(a.lastUpdatedAt),
    );
  }, [rows, orgName]);

  const awaiting = useMemo(
    () => (rows ?? []).filter((r) => awaitsDecision(r.lcySts)).sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    [rows],
  );

  return { auth, gwBase, rows, names, orgs, awaiting, error, setError, busy, reload, decide, orgName };
}
