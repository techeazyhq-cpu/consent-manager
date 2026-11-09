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
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { AnomalyDto } from "api-client";
import Anomalies, { describeAnomaly } from "./Anomalies";
import type { Session } from "./session";

const acknowledged: string[] = [];
let rows: AnomalyDto[] = [];

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  anomalies: {
    list: async () => rows,
    acknowledge: async (_b: string, _t: string, id: string) => {
      acknowledged.push(id);
      rows = [];
    },
  },
}));
vi.mock("./session", () => ({ freshToken: async () => "t" }));

const finding: AnomalyDto = {
  id: "a1",
  dfId: "12345678-aaaa",
  signal: "api-keys-issued",
  windowStart: "2026-11-09T09:00:00Z",
  observed: 400,
  expected: 4.2,
  score: 80,
  detectedAt: "2026-11-09T10:10:00Z",
  status: "OPEN",
  acknowledgedBy: null,
  acknowledgedAt: null,
};

const session = (isAdmin: boolean) => ({ auth: { token: "t", logout: () => {} }, gwBase: "http://gw", isAdmin }) as unknown as Session;

afterEach(() => {
  cleanup();
  acknowledged.length = 0;
  rows = [];
});

describe("Unusual activity page (ADR-112)", () => {
  it("says what was unusual, for which fiduciary, and how much", async () => {
    rows = [finding];
    render(<Anomalies session={session(true)} />);
    expect(await screen.findByText("API keys issued")).toBeTruthy();
    expect(screen.getByText("12345678")).toBeTruthy();
    expect(screen.getByText("400 in the hour, against about 4 usually")).toBeTruthy();
  });

  it("lets an administrator acknowledge a finding, and it leaves the open list", async () => {
    rows = [finding];
    render(<Anomalies session={session(true)} />);
    fireEvent.click(await screen.findByText("Acknowledge"));
    await waitFor(() => expect(acknowledged).toEqual(["a1"]));
    expect(await screen.findByText("Nothing unusual is open.")).toBeTruthy();
  });

  it("offers no acknowledge button to anyone but an administrator", async () => {
    rows = [finding];
    render(<Anomalies session={session(false)} />);
    await screen.findByText("API keys issued");
    expect(screen.queryByText("Acknowledge")).toBeNull();
  });

  it("describes a signal that is usually silent without a made-up number", () => {
    expect(describeAnomaly({ observed: 25, expected: 0 })).toBe("25 in the hour, against under 1 usually");
  });
});
