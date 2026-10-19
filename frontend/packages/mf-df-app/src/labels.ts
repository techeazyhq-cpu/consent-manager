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

import type { CnsArtRdMdl, CnsLcySts } from "api-client";

/** Where a request stands, from the fiduciary's side. */
export const STATUS: Record<CnsLcySts, { label: string; badge: string }> = {
  PENDING: { label: "Awaiting the person", badge: "PENDING" },
  NOTIFICATION_FAILED: { label: "Couldn't notify", badge: "DENIED" },
  ISSUED: { label: "Granted", badge: "ACTIVE" },
  DENIED: { label: "Declined", badge: "DENIED" },
  REVOKED: { label: "Withdrawn", badge: "REVOKED" },
  EXPIRED: { label: "Expired", badge: "EXPIRED" },
};

/** The counts the dashboard and a purpose's page show, each with the statuses it covers. */
export const COUNTS: { key: string; label: string; sts?: CnsLcySts[] }[] = [
  { key: "sent", label: "Sent" },
  { key: "pending", label: "Awaiting the person", sts: ["PENDING"] },
  { key: "granted", label: "Granted", sts: ["ISSUED"] },
  { key: "declined", label: "Declined", sts: ["DENIED"] },
  { key: "withdrawn", label: "Withdrawn", sts: ["REVOKED"] },
  { key: "expired", label: "Expired", sts: ["EXPIRED"] },
  { key: "failed", label: "Couldn't notify", sts: ["NOTIFICATION_FAILED"] },
];

export const countOf = (rows: CnsArtRdMdl[], sts?: CnsLcySts[]) =>
  sts ? rows.filter((r) => sts.includes(r.lcySts)).length : rows.length;

/** Of the people who decided, the share who granted; null before anyone has. */
export function grantRate(rows: CnsArtRdMdl[]): number | null {
  const decided = rows.filter((r) => ["ISSUED", "DENIED", "REVOKED", "EXPIRED"].includes(r.lcySts));
  if (decided.length === 0) return null;
  // withdrawn and expired consents were granted first
  const granted = decided.filter((r) => r.lcySts !== "DENIED").length;
  return Math.round((granted / decided.length) * 100);
}

export const fmtDate = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "—";

/**
 * The data a request covers, as the fiduciary must read it (ADR-026): once given, only what the person granted; what
 * they didn't grant is named, since the fiduciary may not use it.
 */
export function dataLabel(r: Pick<CnsArtRdMdl, "dCatCodes" | "grantedDCatCodes">, humanise: (code: string) => string): string {
  const granted = r.grantedDCatCodes;
  if (!granted) return r.dCatCodes.map(humanise).join(", ");
  const refused = r.dCatCodes.filter((c) => !granted.includes(c));
  return granted.map(humanise).join(", ") + (refused.length ? ` (not given: ${refused.map(humanise).join(", ")})` : "");
}
