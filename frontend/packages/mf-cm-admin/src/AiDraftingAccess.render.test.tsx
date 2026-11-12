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
import type { AiAccessListDto, AiAccessRowDto } from "api-client";
import AiDraftingAccess from "./AiDraftingAccess";
import type { Session } from "./session";

const row = (over: Partial<AiAccessRowDto>): AiAccessRowDto => ({
  dfId: "df-aaaa",
  enabled: false,
  access: "PENDING",
  note: "We write many purposes",
  requestedBy: "u1",
  requestedAt: "2026-11-10T10:00:00Z",
  declineReason: null,
  updatedAt: "2026-11-10T10:00:00Z",
  ...over,
});

let list: AiAccessListDto;
const calls: string[] = [];

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  dfReg: {
    list: async () => [
      { id: "df-aaaa", name: "Acme Retail" },
      { id: "df-bbbb", name: "Beta Bank" },
      { id: "df-cccc", name: "Cedar Clinic" },
    ],
  },
  aiDraftingConfig: {
    accessList: async () => list,
    setAccess: async (_b: string, _t: string, id: string, on: boolean) => {
      calls.push(`set ${id} ${on}`);
      list = on
        ? { pending: list.pending.filter((r) => r.dfId !== id), enabled: [...list.enabled, row({ dfId: id, enabled: true, access: "ENABLED" })] }
        : { ...list, enabled: list.enabled.filter((r) => r.dfId !== id) };
      return row({ dfId: id });
    },
    approveAccess: async (_b: string, _t: string, id: string) => {
      calls.push(`approve ${id}`);
      list = { pending: [], enabled: [row({ dfId: id, enabled: true, access: "ENABLED" })] };
      return row({ dfId: id });
    },
    declineAccess: async (_b: string, _t: string, id: string, reason: string) => {
      calls.push(`decline ${id} ${reason}`);
      list = { pending: [], enabled: [] };
      return row({ dfId: id });
    },
  },
}));
vi.mock("./session", () => ({ freshToken: async () => "t" }));

const session = (isAdmin: boolean) => ({ auth: { token: "t", logout: () => {} }, gwBase: "http://gw", isAdmin }) as unknown as Session;

afterEach(() => {
  cleanup();
  calls.length = 0;
});

describe("Which organisations can draft (ADR-114)", () => {
  it("shows a waiting request by the organisation's name, with its note", async () => {
    list = { pending: [row({})], enabled: [] };
    render(<AiDraftingAccess session={session(true)} />);
    expect(await screen.findByRole("link", { name: "Acme Retail" })).toBeTruthy();
    expect(screen.getByText("We write many purposes")).toBeTruthy();
  });

  it("lets an administrator approve it, and it moves to turned on", async () => {
    list = { pending: [row({})], enabled: [] };
    render(<AiDraftingAccess session={session(true)} />);
    fireEvent.click(await screen.findByText("Approve"));
    await waitFor(() => expect(calls).toEqual(["approve df-aaaa"]));
    expect(await screen.findByText("Turn off for Acme Retail")).toBeTruthy();
  });

  it("will not decline without a reason, and sends the reason it is given", async () => {
    list = { pending: [row({})], enabled: [] };
    render(<AiDraftingAccess session={session(true)} />);
    fireEvent.click(await screen.findByText("Decline…"));
    expect((screen.getByRole("button", { name: "Decline" }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getByLabelText(/Why Acme Retail is turned down/), { target: { value: "Trial account" } });
    fireEvent.click(screen.getByRole("button", { name: "Decline" }));
    await waitFor(() => expect(calls).toEqual(["decline df-aaaa Trial account"]));
  });

  it("turns it on for an organisation already onboarded, offering only those not already on", async () => {
    list = { pending: [], enabled: [row({ dfId: "df-bbbb", enabled: true, access: "ENABLED" })] };
    render(<AiDraftingAccess session={session(false)} />);
    const select = (await screen.findByLabelText("Turn on for an organisation")) as HTMLSelectElement;
    expect([...select.options].map((o) => o.text)).toEqual(["Turn on for an organisation…", "Acme Retail", "Cedar Clinic"]);
    fireEvent.change(select, { target: { value: "df-cccc" } });
    fireEvent.click(screen.getByRole("button", { name: "Turn on" }));
    await waitFor(() => expect(calls).toEqual(["set df-cccc true"]));
  });

  it("lets an operator look and turn on, but not decide or turn off", async () => {
    list = { pending: [row({})], enabled: [row({ dfId: "df-bbbb", enabled: true, access: "ENABLED" })] };
    render(<AiDraftingAccess session={session(false)} />);
    await screen.findByRole("link", { name: "Acme Retail" });
    expect(screen.queryByText("Approve")).toBeNull();
    expect(screen.queryByText(/Turn off for/)).toBeNull();
    expect(screen.getByText(/Only a platform administrator decides a request/)).toBeTruthy();
    expect(screen.getByRole("button", { name: "Turn on" })).toBeTruthy();
  });
});
