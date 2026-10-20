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

import type { CnsArtRdMdl, CnsLcySts, RecordEvent } from "api-client";

/**
 * A data principal's rules, the same in the web portal and the mobile app (ADR-032 §3): where a consent stands in the
 * person's words, what may be given and for how long (ADR-026, ADR-028), the changes allowed after (ADR-026 §4), and
 * how a record's history reads (ADR-046). No React, no DOM: plain functions both apps test against.
 */

const DAY_MS = 86_400_000;

/** A code such as {@code ORDER_FULFILLMENT} as words a person reads: "Order fulfillment". */
export function humanise(code: string): string {
  const words = code.replace(/[_-]+/g, " ").trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/**
 * Where a consent stands, in the person's terms. A request whose notification failed is still theirs to decide, so
 * it reads as waiting for them.
 */
export const STATUS: Record<CnsLcySts, { label: string; badge: string }> = {
  PENDING: { label: "Awaiting your decision", badge: "PENDING" },
  NOTIFICATION_FAILED: { label: "Awaiting your decision", badge: "PENDING" },
  ISSUED: { label: "Given", badge: "ACTIVE" },
  DENIED: { label: "Declined", badge: "DENIED" },
  REVOKED: { label: "Withdrawn", badge: "REVOKED" },
  EXPIRED: { label: "Expired", badge: "EXPIRED" },
};

export const awaitsDecision = (sts: CnsLcySts) => sts === "PENDING" || sts === "NOTIFICATION_FAILED";

/** Awaiting the person first, then in force, then the rest. */
export function statusOrder(sts: CnsLcySts): number {
  return awaitsDecision(sts) ? 0 : sts === "ISSUED" ? 1 : 2;
}

export const fmtDate = (iso?: string | null) =>
  iso ? new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : null;

export type Decision = "give" | "decline" | "withdraw";

/** A change to a consent in force (ADR-026 §4): end it sooner, stop sharing one category, or renew it. */
export type Change =
  | { kind: "shorten"; until: string }
  | { kind: "withdraw-dcat"; dCat: string }
  | { kind: "renew"; days: number };

/** How close to its end a consent is when the person is prompted to renew it. */
export const RENEW_PROMPT_DAYS = 14;

/** Consents in force that end within {@link RENEW_PROMPT_DAYS}, soonest first: prompts to renew before a lapse. */
export function endingSoon(rows: CnsArtRdMdl[], now: Date = new Date()): CnsArtRdMdl[] {
  const limit = now.getTime() + RENEW_PROMPT_DAYS * DAY_MS;
  return rows
    .filter((r) => r.lcySts === "ISSUED" && r.validUntil && new Date(r.validUntil).getTime() <= limit)
    .sort((a, b) => (a.validUntil ?? "").localeCompare(b.validUntil ?? ""));
}

/** A date as a date picker holds it (yyyy-mm-dd, local), `days` after `from`. */
export function dateAfter(days: number, from: Date = new Date()): string {
  const d = new Date(from.getFullYear(), from.getMonth(), from.getDate() + days);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Whole days from `from`'s date to a yyyy-mm-dd date; NaN when it isn't a date. */
export function daysUntil(date: string, from: Date = new Date()): number {
  const [y, m, d] = date.split("-").map(Number);
  if (!y || !m || !d) return NaN;
  const start = new Date(from.getFullYear(), from.getMonth(), from.getDate());
  return Math.round((new Date(y, m - 1, d).getTime() - start.getTime()) / DAY_MS);
}

/** Why the choice can't be given yet, or null when it can. */
export function choiceProblem(chosen: string[], days: number, maxDays: number): string | null {
  if (chosen.length === 0) return "Tick the data you agree to share, or decline.";
  if (!Number.isFinite(days) || days < 1) return "Choose a date after today.";
  if (days > maxDays) return `Choose a date no more than ${maxDays} days from today.`;
  return null;
}

/** What the give button says: all of it, or how many of the categories asked. */
export function giveLabel(chosen: number, asked: number): string {
  return chosen === 0 || chosen === asked ? "Give consent" : `Give consent to ${chosen} of ${asked}`;
}

/** Whole days until `until`, rounded up: a consent ending later today has 1 day left. */
export function daysLeft(until: string, now: Date = new Date()): number {
  return Math.max(0, Math.ceil((new Date(until).getTime() - now.getTime()) / DAY_MS));
}

/**
 * The instant a consent ending at `until` ends instead on date `date` (yyyy-mm-dd): the same time of day, the
 * difference in calendar days earlier, so ending "a week sooner" ends exactly a week sooner.
 */
export function shortenedUntil(until: string, date: string): string {
  const current = new Date(until);
  const days = daysUntil(date, current);
  return new Date(current.getTime() + days * DAY_MS).toISOString();
}

/** What each record event means, in the person's terms. */
export const EVENT_LABELS: Record<RecordEvent["type"], string> = {
  requested: "Asked for your consent",
  given: "You gave consent",
  refused: "You declined",
  modified: "You changed it",
  renewed: "You renewed it",
  withdrawn: "You withdrew consent",
  lapsed: "It ended: its validity ran out",
};

/** A change's detail in words: "Ended sooner", "Stopped sharing Name". */
export function changeLabel(change?: string): string | null {
  if (!change) return null;
  if (change === "SHORTENED") return "Ended sooner";
  if (change.startsWith("CATEGORY_WITHDRAWN:")) return `Stopped sharing ${humanise(change.split(":")[1])}`;
  if (change.startsWith("BACKFILLED")) return "Recorded when records began; the exact time is in the audit log";
  return change;
}

/** The JSON a compact JWS signs, decoded (base64url, UTF-8); null when it isn't a JWS. */
export function jwsPayload(jws: string): unknown {
  const part = jws.split(".")[1];
  if (!part) return null;
  try {
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}
