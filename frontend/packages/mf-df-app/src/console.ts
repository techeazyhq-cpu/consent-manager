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

import { useCallback, useEffect, useMemo, useState } from "react";
import type { AuthInfo } from "auth-kit";
import { ApiError, cnsCmd, cnsQry, dfReg, type CnsArtRdMdl, type DfRole, type ExtDpRefTyp } from "api-client";
import { friendlyError } from "ui-kit/src/index";
import { freshToken } from "./token";

/** One purpose the fiduciary asks consent for, for its tile. */
export interface Purpose {
  code: string;
  sent: number;
  granted: number;
  pending: number;
  lastAt: string;
}

export interface NewRequest {
  extDpRefTyp: ExtDpRefTyp;
  extDpRefVal: string;
  prpsCode: string;
  dCatCodes: string[];
  ntcTitle: string;
  ntcText: string;
}

export interface Console {
  auth: AuthInfo;
  gwBase: string;
  dfId: string;
  role: DfRole;
  dfName: string;
  rows: CnsArtRdMdl[] | null;
  purposes: Purpose[];
  error: string | null;
  reload: () => Promise<void>;
  /** Raises a request and waits until it shows in the list; the error's message, or null on success. */
  raise: (req: NewRequest) => Promise<string | null>;
}

/** Everything the console's pages share: the fiduciary, its requests, and raising new ones. */
export function useConsole(auth: AuthInfo, gwBase: string, dfId: string, role: DfRole): Console {
  const [rows, setRows] = useState<CnsArtRdMdl[] | null>(null);
  const [dfName, setDfName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const fetchRows = useCallback(async () => {
    const data = await cnsQry.listMine(gwBase, await freshToken(auth));
    setRows(data);
    return data;
  }, [auth, gwBase]);

  const reload = useCallback(async () => {
    setError(null);
    try {
      await fetchRows();
    } catch (err) {
      setError(friendlyError(err));
      setRows((prev) => prev ?? []);
    }
  }, [fetchRows]);

  useEffect(() => {
    reload();
    freshToken(auth)
      .then((token) => dfReg.getOne(gwBase, token, dfId))
      .then((df) => setDfName(df.name))
      .catch(() => {});
  }, [auth, gwBase, dfId, reload]);

  const raise = useCallback(
    async (req: NewRequest): Promise<string | null> => {
      try {
        const created = await cnsCmd.create(gwBase, await freshToken(auth), req);
        // The request is written at once but shows here only once the read model has projected it: re-read briefly.
        for (let attempt = 0; attempt < 8; attempt++) {
          const data = await fetchRows();
          if (data.some((r) => r.cnsReqId === created.id)) break;
          await new Promise((r) => setTimeout(r, 600));
        }
        return null;
      } catch (err) {
        if (err instanceof ApiError && err.status === 402) {
          return "Your subscription isn't active, so new requests are paused. Contact the platform to reactivate it.";
        }
        if (err instanceof ApiError && err.status === 429) return "This month's request allowance is used up.";
        return friendlyError(err);
      }
    },
    [auth, gwBase, fetchRows],
  );

  const purposes = useMemo<Purpose[]>(() => {
    const byCode = new Map<string, Purpose>();
    for (const r of rows ?? []) {
      const p = byCode.get(r.prpsCode) ?? { code: r.prpsCode, sent: 0, granted: 0, pending: 0, lastAt: r.createdAt };
      p.sent += 1;
      if (r.lcySts === "ISSUED") p.granted += 1;
      if (r.lcySts === "PENDING") p.pending += 1;
      if (r.createdAt > p.lastAt) p.lastAt = r.createdAt;
      byCode.set(r.prpsCode, p);
    }
    return Array.from(byCode.values()).sort((a, b) => b.lastAt.localeCompare(a.lastAt));
  }, [rows]);

  return { auth, gwBase, dfId, role, dfName: dfName || "Your organisation", rows, purposes, error, reload, raise };
}
