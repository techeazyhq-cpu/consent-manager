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

import type { InsightOutcomes } from "api-client";

/** The windows the dashboard offers, in days. */
export const PERIODS = [
  { days: 7, label: "7 days" },
  { days: 30, label: "30 days" },
  { days: 90, label: "90 days" },
  { days: 365, label: "12 months" },
];

/** What the dashboard is showing: the last N days, or one calendar month (ADR-088), quarter or year (ADR-102). */
export type Window =
  | { kind: "days"; days: number }
  | { kind: "month"; month: string }
  | { kind: "quarter"; quarter: string }
  | { kind: "year"; year: string };

/** The quarter as `yyyy-Qn`, counting back from {@code now} (0 is this quarter, 1 the last), in UTC. */
export function quartersAgo(back: number, now: Date = new Date()): string {
  const index = now.getUTCFullYear() * 4 + Math.floor(now.getUTCMonth() / 3) - back;
  return `${Math.floor(index / 4)}-Q${(index % 4) + 1}`;
}

/** The year as `yyyy`, counting back from {@code now} (0 is this year), in UTC. */
export function yearsAgo(back: number, now: Date = new Date()): string {
  return String(now.getUTCFullYear() - back);
}

/** The calendar periods offered beyond the two months: this quarter so far, and the year so far. */
export function quarterAndYearWindows(now: Date = new Date()): { window: Window; label: string }[] {
  return [
    { window: { kind: "quarter", quarter: quartersAgo(0, now) }, label: "This quarter" },
    { window: { kind: "year", year: yearsAgo(0, now) }, label: "Year to date" },
  ];
}

/** The key that tells one window from another: the number of days, or the month, quarter or year asked for. */
const windowKey = (w: Window) => (w.kind === "days" ? String(w.days) : w.kind === "month" ? w.month : w.kind === "quarter" ? w.quarter : w.year);

/** The month as `yyyy-MM`, counting back from {@code now} (0 is this month, 1 the last), in UTC. */
export function monthsAgo(back: number, now: Date = new Date()): string {
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - back, 1));
  return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

/** The two calendar months offered, worked out from today. */
export function monthWindows(now: Date = new Date()): { window: Window; label: string }[] {
  return [
    { window: { kind: "month", month: monthsAgo(0, now) }, label: "This month" },
    { window: { kind: "month", month: monthsAgo(1, now) }, label: "Last month" },
  ];
}

export const sameWindow = (a: Window, b: Window) => a.kind === b.kind && windowKey(a) === windowKey(b);

/**
 * What a comparison is against, in words that follow "on the": "30 days before" for a span of days; for a month still going "same days
 * of last month"; for a month that has ended "month before".
 */
export function comparisonSpan(w: Window, now: Date = new Date()): string {
  switch (w.kind) {
    case "days":
      return `${w.days} days before`;
    case "month":
      return w.month === monthsAgo(0, now) ? "same days of last month" : "month before";
    case "quarter":
      return w.quarter === quartersAgo(0, now) ? "same days of last quarter" : "quarter before";
    case "year":
      return w.year === yearsAgo(0, now) ? "same days of last year" : "year before";
  }
}

/** The month, quarter or year a window asks for, as the three query values the API takes; none of them for a span of days. */
export function calendarOf(w: Window): { month?: string; quarter?: string; year?: string } {
  return w.kind === "month" ? { month: w.month } : w.kind === "quarter" ? { quarter: w.quarter } : w.kind === "year" ? { year: w.year } : {};
}

/** What the figures are for, in words: "October 2026", "Q3 2026", "2026 so far"; null for a span of days. */
export function calendarTitle(w: Window, now: Date = new Date()): string | null {
  switch (w.kind) {
    case "month":
      return new Date(`${w.month}-01T00:00:00Z`).toLocaleDateString(undefined, { month: "long", year: "numeric", timeZone: "UTC" });
    case "quarter": {
      const [year, q] = w.quarter.split("-");
      return `${q} ${year}`;
    }
    case "year":
      return w.year === yearsAgo(0, now) ? `${w.year} so far` : w.year;
    default:
      return null;
  }
}

/** A duration in seconds as a person says it: "45 s", "12 min", "3.5 h", "2 days"; "—" when there is none. */
export function fmtDuration(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  if (seconds < 60) return `${Math.round(seconds)} s`;
  if (seconds < 3600) return `${Math.round(seconds / 60)} min`;
  if (seconds < 86_400) return `${+(seconds / 3600).toFixed(1)} h`;
  const days = +(seconds / 86_400).toFixed(1);
  return `${days} ${days === 1 ? "day" : "days"}`;
}

/** Of those who decided, the share who gave consent, in whole percent; null when nobody decided. */
export function decidedRate(o: Pick<InsightOutcomes, "given" | "refused">): number | null {
  const decided = o.given + o.refused;
  return decided === 0 ? null : Math.round((100 * o.given) / decided);
}

/**
 * How a figure moved against the window just before, in a few words: "up 20% on the 30 days before", "down 3", "same as the
 * 30 days before", or "nothing in the 30 days before" when there is nothing to compare with. Undefined when no comparison is shown.
 */
export function changeNote(now: number, before: number | undefined, span: number | string): string | undefined {
  if (before === undefined) return undefined;
  const window = typeof span === "number" ? `${span} days before` : span;
  if (before === 0) return now === 0 ? `same as the ${window}` : `none in the ${window}`;
  if (now === before) return `same as the ${window}`;
  const pct = Math.round((100 * Math.abs(now - before)) / before);
  return `${now > before ? "up" : "down"} ${pct}% on the ${window}`;
}

/** A share in whole percent, or null when there is nothing to share out. */
export const share = (part: number, of: number): number | null => (of === 0 ? null : Math.round((100 * part) / of));

/** The top of a bar chart's scale: the largest value rounded up to 1, 2 or 5 times a power of ten (at least 1). */
export function niceMax(max: number): number {
  if (max <= 1) return 1;
  const pow = 10 ** Math.floor(Math.log10(max));
  for (const step of [1, 2, 5, 10]) {
    if (step * pow >= max) return step * pow;
  }
  return 10 * pow;
}

/** "20 Oct" for a UTC calendar date. */
export const fmtDay = (date: string) =>
  new Date(`${date}T00:00:00Z`).toLocaleDateString(undefined, { day: "numeric", month: "short", timeZone: "UTC" });
