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
import Onboard from "./Onboard";
import type { Session } from "./session";

const steps: string[] = [];

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  bil: { listSubPlns: async () => [] },
  dfReg: {
    register: async () => {
      steps.push("register");
      return { id: "df-new" };
    },
    activate: async () => {
      steps.push("activate");
      return {};
    },
    uploadLogo: async () => {},
  },
  aiDraftingConfig: {
    setAccess: async (_b: string, _t: string, id: string, on: boolean) => {
      steps.push(`ai ${id} ${on}`);
      return {};
    },
  },
}));
vi.mock("./session", () => ({ freshToken: async () => "t" }));

const session = { auth: { token: "t", logout: () => {} }, gwBase: "http://gw", isAdmin: false } as unknown as Session;

afterEach(() => {
  cleanup();
  steps.length = 0;
});

const fill = () => {
  fireEvent.change(screen.getByLabelText(/Display name/), { target: { value: "Acme Retail" } });
  fireEvent.change(screen.getByLabelText(/Contact email/), { target: { value: "owner@acme.example" } });
};

describe("Onboarding chooses AI drafting (ADR-114)", () => {
  it("leaves it off unless chosen, and says the tenant can ask later", async () => {
    render(<Onboard session={session} />);
    fill();
    expect((screen.getByLabelText(/Draft purposes with the AI model/) as HTMLInputElement).checked).toBe(false);
    expect(screen.getByText(/ask for it later from its console/)).toBeTruthy();
    fireEvent.click(screen.getByRole("button", { name: "Onboard tenant" }));
    await waitFor(() => expect(steps).toContain("activate"));
    expect(steps.some((s) => s.startsWith("ai "))).toBe(false);
  });

  it("turns it on for the new tenant, after activating it, when chosen", async () => {
    render(<Onboard session={session} />);
    fill();
    fireEvent.click(screen.getByLabelText(/Draft purposes with the AI model/));
    fireEvent.click(screen.getByRole("button", { name: "Onboard tenant" }));
    await waitFor(() => expect(steps).toEqual(["register", "activate", "ai df-new true"]));
  });
});
