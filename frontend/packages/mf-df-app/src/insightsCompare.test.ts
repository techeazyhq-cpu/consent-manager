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

import { describe, expect, it } from "vitest";
import {
  calendarOf,
  calendarTitle,
  changeNote,
  comparisonSpan,
  decidedRate,
  monthWindows,
  monthsAgo,
  purposeRows,
  quarterAndYearWindows,
  quartersAgo,
  rateChange,
  sameWindow,
  shortChange,
  yearsAgo,
} from "./dashboard";
import type { InsightOutcomes } from "api-client";

describe("changeNote", () => {
  it("says nothing when no comparison is shown", () => {
    expect(changeNote(5, undefined, 30)).toBeUndefined();
  });

  it("says how a figure moved against the window before, in whole percent", () => {
    expect(changeNote(12, 10, 30)).toBe("up 20% on the 30 days before");
    expect(changeNote(7, 10, 7)).toBe("down 30% on the 7 days before");
    expect(changeNote(10, 10, 90)).toBe("same as the 90 days before");
  });

  it("does not divide by nothing", () => {
    expect(changeNote(4, 0, 30)).toBe("none in the 30 days before");
    expect(changeNote(0, 0, 30)).toBe("same as the 30 days before");
  });
});

// 20 October 2026, in the middle of the fourth quarter
const NOW = new Date(Date.UTC(2026, 9, 20, 10, 0, 0));

describe("calendar months, quarters and years (ADR-088, ADR-102)", () => {
  it("counts months back across the new year", () => {
    expect(monthsAgo(0, NOW)).toBe("2026-10");
    expect(monthsAgo(1, NOW)).toBe("2026-09");
    expect(monthsAgo(10, NOW)).toBe("2025-12");
  });

  it("counts quarters back across the new year", () => {
    expect(quartersAgo(0, NOW)).toBe("2026-Q4");
    expect(quartersAgo(1, NOW)).toBe("2026-Q3");
    expect(quartersAgo(3, NOW)).toBe("2026-Q1");
    expect(quartersAgo(4, NOW)).toBe("2025-Q4");
    expect(quartersAgo(0, new Date(Date.UTC(2026, 0, 1)))).toBe("2026-Q1");
    expect(quartersAgo(0, new Date(Date.UTC(2026, 2, 31)))).toBe("2026-Q1");
    expect(quartersAgo(0, new Date(Date.UTC(2026, 3, 1)))).toBe("2026-Q2");
  });

  it("counts years back", () => {
    expect(yearsAgo(0, NOW)).toBe("2026");
    expect(yearsAgo(2, NOW)).toBe("2024");
  });

  it("offers this quarter and the year to date", () => {
    expect(quarterAndYearWindows(NOW)).toEqual([
      { window: { kind: "quarter", quarter: "2026-Q4" }, label: "This quarter" },
      { window: { kind: "year", year: "2026" }, label: "Year to date" },
    ]);
    expect(monthWindows(NOW).map((w) => w.label)).toEqual(["This month", "Last month"]);
  });

  it("tells windows apart by kind and by what they ask for", () => {
    expect(sameWindow({ kind: "days", days: 30 }, { kind: "days", days: 30 })).toBe(true);
    expect(sameWindow({ kind: "days", days: 30 }, { kind: "days", days: 7 })).toBe(false);
    expect(sameWindow({ kind: "quarter", quarter: "2026-Q4" }, { kind: "quarter", quarter: "2026-Q4" })).toBe(true);
    expect(sameWindow({ kind: "quarter", quarter: "2026-Q4" }, { kind: "quarter", quarter: "2026-Q3" })).toBe(false);
    // a year and a month can never be the same window, whatever their text
    expect(sameWindow({ kind: "year", year: "2026" }, { kind: "month", month: "2026" })).toBe(false);
  });

  it("words the comparison by what is still going", () => {
    expect(comparisonSpan({ kind: "days", days: 30 }, NOW)).toBe("30 days before");
    expect(comparisonSpan({ kind: "month", month: "2026-10" }, NOW)).toBe("same days of last month");
    expect(comparisonSpan({ kind: "month", month: "2026-09" }, NOW)).toBe("month before");
    expect(comparisonSpan({ kind: "quarter", quarter: "2026-Q4" }, NOW)).toBe("same days of last quarter");
    expect(comparisonSpan({ kind: "quarter", quarter: "2026-Q3" }, NOW)).toBe("quarter before");
    expect(comparisonSpan({ kind: "year", year: "2026" }, NOW)).toBe("same days of last year");
    expect(comparisonSpan({ kind: "year", year: "2025" }, NOW)).toBe("year before");
  });

  it("gives the API the one thing a window asks for, and a title in words", () => {
    expect(calendarOf({ kind: "days", days: 30 })).toEqual({});
    expect(calendarOf({ kind: "month", month: "2026-09" })).toEqual({ month: "2026-09" });
    expect(calendarOf({ kind: "quarter", quarter: "2026-Q3" })).toEqual({ quarter: "2026-Q3" });
    expect(calendarOf({ kind: "year", year: "2026" })).toEqual({ year: "2026" });
    expect(calendarTitle({ kind: "days", days: 30 }, NOW)).toBeNull();
    expect(calendarTitle({ kind: "quarter", quarter: "2026-Q3" }, NOW)).toBe("Q3 2026");
    expect(calendarTitle({ kind: "year", year: "2026" }, NOW)).toBe("2026 so far");
    expect(calendarTitle({ kind: "year", year: "2025" }, NOW)).toBe("2025");
  });
});

const o = (raised: number, given = 0): InsightOutcomes => ({ raised, given, refused: 0, awaiting: 0, notNotified: 0 });

describe("purposes beside the window before (ADR-104)", () => {
  it("says how a table figure moved, in a few characters", () => {
    expect(shortChange(12, undefined)).toBeUndefined();
    expect(shortChange(12, 10)).toBe("+20%");
    expect(shortChange(7, 10)).toBe("-30%");
    expect(shortChange(10, 10)).toBe("same");
    expect(shortChange(4, 0)).toBe("new");
    expect(shortChange(0, 0)).toBeUndefined();
    expect(shortChange(0, 5)).toBe("-100%");
  });

  it("is the purposes as they came when not comparing", () => {
    const rows = purposeRows([{ prpsCode: "NEWS", outcomes: o(2), medianSeconds: 60 }], undefined);
    expect(rows).toEqual([{ prpsCode: "NEWS", outcomes: o(2), medianSeconds: 60 }]);
    expect(rows[0].before).toBeUndefined();
  });

  it("puts the window before's outcomes beside each purpose, and zeros for one that is new", () => {
    const rows = purposeRows(
      [
        { prpsCode: "ORDERS", outcomes: o(6, 4), medianSeconds: 60 },
        { prpsCode: "NEW", outcomes: o(3, 1), medianSeconds: null },
      ],
      [{ prpsCode: "ORDERS", outcomes: o(5, 3), medianSeconds: 60 }],
    );
    expect(rows.map((r) => r.prpsCode)).toEqual(["ORDERS", "NEW"]);
    expect(rows[0].before).toEqual(o(5, 3));
    expect(rows[1].before).toEqual(o(0));
  });

  it("lists a purpose that had requests before and none now, last, with zeros now", () => {
    const rows = purposeRows(
      [{ prpsCode: "ORDERS", outcomes: o(6), medianSeconds: 60 }],
      [
        { prpsCode: "QUIET", outcomes: o(2), medianSeconds: 30 },
        { prpsCode: "ORDERS", outcomes: o(5), medianSeconds: 60 },
        { prpsCode: "QUIETER", outcomes: o(9), medianSeconds: 30 },
      ],
    );
    expect(rows.map((r) => r.prpsCode)).toEqual(["ORDERS", "QUIETER", "QUIET"]);
    expect(rows[1]).toEqual({ prpsCode: "QUIETER", outcomes: o(0), medianSeconds: null, before: o(9) });
    expect(shortChange(rows[1].outcomes.raised, rows[1].before?.raised)).toBe("-100%");
  });
});

describe("the grant rate beside the window before (ADR-106)", () => {
  it("moves in percentage points, not percent of a percent", () => {
    expect(rateChange(80, 75)).toBe("+5 pts");
    expect(rateChange(60, 63)).toBe("-3 pts");
    expect(rateChange(50, 50)).toBe("same");
    expect(rateChange(100, 0)).toBe("+100 pts");
  });

  it("says nothing when either window has no rate to compare", () => {
    expect(rateChange(null, 50)).toBeUndefined();
    expect(rateChange(50, null)).toBeUndefined();
    expect(rateChange(50, undefined)).toBeUndefined();
    expect(rateChange(null, null)).toBeUndefined();
  });

  it("works from a purpose's outcomes as the table has them", () => {
    const now: InsightOutcomes = { raised: 10, given: 8, refused: 2, awaiting: 0, notNotified: 0 };
    const before: InsightOutcomes = { raised: 8, given: 4, refused: 4, awaiting: 0, notNotified: 0 };
    expect(rateChange(decidedRate(now), decidedRate(before))).toBe("+30 pts");
    // nobody decided before: no rate then, so nothing to say
    expect(rateChange(decidedRate(now), decidedRate({ given: 0, refused: 0 }))).toBeUndefined();
  });

  it("gives refused and awaiting the same short change as raised and given", () => {
    expect(shortChange(6, 3)).toBe("+100%");
    expect(shortChange(0, 4)).toBe("-100%");
  });
});
