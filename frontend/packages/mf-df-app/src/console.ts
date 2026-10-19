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
import {
  ApiError,
  cnsCmd,
  cnsQry,
  dfReg,
  prps,
  type CnsArtRdMdl,
  type CnsReqBatchTemplate,
  type DfRole,
  type ExtDpRefTyp,
  type PrpsSummaryDto,
} from "api-client";
import { friendlyError } from "ui-kit/src/index";
import { freshToken } from "./token";

/**
 * One purpose for its tile: as defined (ADR-031), with how its requests are going. A code used by requests but not
 * defined here (none since purposes were versioned) still shows, so no request is ever hidden.
 */
export interface Purpose {
  code: string;
  name: string;
  /** The published version requests are made from; null until one is published. */
  currentVer: number | null;
  draftVer: number | null;
  sent: number;
  granted: number;
  pending: number;
  lastAt: string;
}

/** A request for one person: who, and the purpose version asked for. */
export type NewRequest = CnsReqBatchTemplate & {
  extDpRefTyp: ExtDpRefTyp;
  extDpRefVal: string;
};

export interface Console {
  auth: AuthInfo;
  gwBase: string;
  dfId: string;
  role: DfRole;
  dfName: string;
  rows: CnsArtRdMdl[] | null;
  /** The organisation's purpose definitions; null while loading. */
  defs: PrpsSummaryDto[] | null;
  purposes: Purpose[];
  error: string | null;
  reload: () => Promise<void>;
  reloadDefs: () => Promise<void>;
  /** Raises a request and waits until it shows in the list; the error's message, or null on success. */
  raise: (req: NewRequest) => Promise<string | null>;
}

/** Everything the console's pages share: the fiduciary, its requests, and raising new ones. */
export function useConsole(auth: AuthInfo, gwBase: string, dfId: string, role: DfRole): Console {
  const [rows, setRows] = useState<CnsArtRdMdl[] | null>(null);
  const [defs, setDefs] = useState<PrpsSummaryDto[] | null>(null);
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

  const reloadDefs = useCallback(async () => {
    try {
      setDefs(await prps.list(gwBase, await freshToken(auth)));
    } catch (err) {
      setError(friendlyError(err));
      setDefs((prev) => prev ?? []);
    }
  }, [auth, gwBase]);

  useEffect(() => {
    reload();
    reloadDefs();
    freshToken(auth)
      .then((token) => dfReg.getOne(gwBase, token, dfId))
      .then((df) => setDfName(df.name))
      .catch(() => {});
  }, [auth, gwBase, dfId, reload, reloadDefs]);

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
        if (err instanceof ApiError && err.problem?.title === "purpose-version-superseded") {
          reloadDefs();
          return "That purpose has a newer version now. Check what you're asking and send again.";
        }
        return friendlyError(err);
      }
    },
    [auth, gwBase, fetchRows, reloadDefs],
  );

  const purposes = useMemo<Purpose[]>(() => purposeTiles(defs ?? [], rows ?? []), [defs, rows]);

  return {
    auth,
    gwBase,
    dfId,
    role,
    dfName: dfName || "Your organisation",
    rows,
    defs,
    purposes,
    error,
    reload,
    reloadDefs,
    raise,
  };
}

/** Every defined purpose and every code requests used, with their counts; most recently active first. */
export function purposeTiles(defs: PrpsSummaryDto[], rows: CnsArtRdMdl[]): Purpose[] {
  const byCode = new Map<string, Purpose>();
  for (const d of defs) {
    byCode.set(d.code, {
      code: d.code,
      name: (d.current ?? d.draft)?.name ?? d.code,
      currentVer: d.currentVer,
      draftVer: d.draftVer,
      sent: 0,
      granted: 0,
      pending: 0,
      lastAt: d.updatedAt,
    });
  }
  for (const r of rows) {
    const p = byCode.get(r.prpsCode) ?? {
      code: r.prpsCode,
      name: r.prpsCode,
      currentVer: null,
      draftVer: null,
      sent: 0,
      granted: 0,
      pending: 0,
      lastAt: r.createdAt,
    };
    p.sent += 1;
    if (r.lcySts === "ISSUED") p.granted += 1;
    if (r.lcySts === "PENDING") p.pending += 1;
    if (r.createdAt > p.lastAt) p.lastAt = r.createdAt;
    byCode.set(r.prpsCode, p);
  }
  return Array.from(byCode.values()).sort((a, b) => b.lastAt.localeCompare(a.lastAt));
}
