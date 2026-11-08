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
import type { ProcessorDto } from "api-client";
import Processors from "./Processors";
import type { Console } from "./console";

const created: unknown[] = [];
const REGISTER: ProcessorDto[] = [
  { id: "p1", name: "Courier Ltd", role: "Delivers orders", country: "IN", contactEmail: "ops@courier.example", status: "ACTIVE", createdAt: "2026-11-01T00:00:00Z" },
];

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  processors: {
    list: async () => REGISTER,
    create: async (_b: string, _t: string, p: unknown) => {
      created.push(p);
      return REGISTER[0];
    },
    uses: async () => [],
    retire: async () => REGISTER[0],
    reinstate: async () => REGISTER[0],
  },
}));
vi.mock("./token", () => ({ freshToken: async () => "t" }));

const console_ = { auth: { token: "t", logout: () => {} }, gwBase: "http://gw", role: "OWNER" } as unknown as Console;

afterEach(() => {
  cleanup();
  created.length = 0;
});

describe("Processors page", () => {
  it("shows the register and a form whose fields are real inputs", async () => {
    render(<Processors console={console_} />);

    expect(await screen.findByText("Courier Ltd")).toBeTruthy();
    // each label must name a control the person can type in
    for (const label of ["Name", "What it does with the data", "Country (two letters)", "Contact email"]) {
      expect(screen.getByLabelText(label).tagName).toBe("INPUT");
    }
  });

  it("adds a processor with what was typed", async () => {
    render(<Processors console={console_} />);
    await screen.findByText("Courier Ltd");

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Cloud Host" } });
    fireEvent.change(screen.getByLabelText("Country (two letters)"), { target: { value: "DE" } });
    fireEvent.click(screen.getByRole("button", { name: "Add" }));

    await waitFor(() => expect(created).toEqual([{ name: "Cloud Host", role: "", country: "DE", contactEmail: "" }]));
  });

  it("asks before retiring, naming where the processor is still used", async () => {
    render(<Processors console={console_} />);
    await screen.findByText("Courier Ltd");

    fireEvent.click(screen.getByRole("button", { name: "Retire" }));

    expect(await screen.findByText(/can no longer be named by a new purpose or draft/)).toBeTruthy();
  });
});
