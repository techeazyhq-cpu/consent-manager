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
export function changeNote(now: number, before: number | undefined, days: number): string | undefined {
  if (before === undefined) return undefined;
  const window = `${days} days before`;
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
