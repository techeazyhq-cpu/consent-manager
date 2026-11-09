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

import { afterEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import type { DfInsightsDto, InsightOutcomes } from "api-client";
import { Insights } from "./Insights";
import type { Console } from "./console";

const o = (raised: number, given: number, refused: number, awaiting = 0): InsightOutcomes => ({ raised, given, refused, awaiting, notNotified: 0 });
const decisions = { decided: 0, medianSeconds: null, p90Seconds: null, withinADay: 0 };
const changes = { withdrawn: 0, narrowed: 0, renewed: 0, lapsed: 0 };

const NOW: DfInsightsDto = {
  from: "2026-10-01T00:00:00Z",
  to: "2026-11-01T00:00:00Z",
  outcomes: o(16, 10, 4, 2),
  decisions,
  purposes: [
    { prpsCode: "ORDER_UPDATES", outcomes: o(12, 9, 1, 2), medianSeconds: 60 },
    { prpsCode: "NEW_THING", outcomes: o(4, 1, 3), medianSeconds: null },
  ],
  days: [],
  changes,
  zone: "UTC",
  previous: {
    from: "2026-09-01T00:00:00Z",
    to: "2026-10-01T00:00:00Z",
    outcomes: o(16, 8, 6, 0),
    decisions,
    changes,
    purposes: [
      { prpsCode: "ORDER_UPDATES", outcomes: o(10, 5, 5), medianSeconds: 60 },
      { prpsCode: "OLD_THING", outcomes: o(6, 3, 1, 2), medianSeconds: 30 },
    ],
  },
};

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  insights: { get: async () => NOW, exportCsv: async () => new Blob([]) },
}));
vi.mock("./token", () => ({ freshToken: async () => "t" }));

const console_ = { auth: { token: "t", logout: () => {} }, gwBase: "http://gw", role: "OWNER" } as unknown as Console;

afterEach(cleanup);

/** The cells of the By purpose table's row for a purpose, as text. */
const rowOf = (name: RegExp) => {
  const row = screen.getByText(name).closest("tr") as HTMLElement;
  return within(row).getAllByRole("cell").map((c) => c.textContent ?? "");
};

describe("the By purpose table beside the window before (ADR-104, ADR-106)", () => {
  it("shows how every figure of a purpose moved, the rate in points", async () => {
    render(<Insights console={console_} onFilter={() => {}} />);
    await screen.findByText("By purpose");
    fireEvent.click(screen.getByLabelText(/Compare with the/));

    // ORDER_UPDATES: raised 10 -> 12, given 5 -> 9, refused 5 -> 1, awaiting 0 -> 2, rate 50% -> 90%
    const [, raised, given, refused, awaiting, rate] = await waitFor(() => {
      const cells = rowOf(/Order updates/i);
      expect(cells[1]).toContain("+20%");
      return cells;
    });
    expect(raised).toContain("12");
    expect(given).toContain("+80%");
    expect(refused).toContain("-80%");
    expect(awaiting).toContain("new");
    expect(rate).toContain("90%");
    expect(rate).toContain("+40 pts");
  });

  it("marks a new purpose, and lists one that went quiet last with its figures falling to nothing", async () => {
    render(<Insights console={console_} onFilter={() => {}} />);
    await screen.findByText("By purpose");
    fireEvent.click(screen.getByLabelText(/Compare with the/));

    await waitFor(() => expect(rowOf(/New thing/i)[1]).toContain("new"));
    const quiet = rowOf(/Old thing/i);
    expect(quiet[1]).toContain("-100%");
    expect(quiet[2]).toContain("-100%");
    const names = screen.getAllByRole("row").map((r) => r.textContent ?? "");
    expect(names.findIndex((t) => /Old thing/i.test(t))).toBeGreaterThan(names.findIndex((t) => /New thing/i.test(t)));
  });
});
