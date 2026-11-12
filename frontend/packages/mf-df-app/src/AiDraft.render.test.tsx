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
import { AiDraftPanel, AiReview, allTicked } from "./AiDraft";
import type { Console } from "./console";

let enabled = true;
let offered = true;
let access = "ENABLED";
let declineReason: string | null = null;
const asked: (string | undefined)[] = [];
let withdrawn = 0;

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  prps: {
    aiStatus: async () => ({
      enabled: enabled && offered && access === "ENABLED",
      model: "llama3.2:3b",
      minChars: 20,
      maxChars: 2000,
      modelOffered: enabled && offered,
      access,
      requestedAt: access === "PENDING" ? "2026-11-12T10:00:00Z" : null,
      declineReason,
    }),
    aiDraft: async () => ({}),
    aiRequestAccess: async (_b: string, _t: string, note?: string) => {
      asked.push(note);
      access = "PENDING";
      return { access };
    },
    aiWithdrawAccess: async () => {
      withdrawn++;
      access = "NOT_ENABLED";
      return { access };
    },
  },
}));
vi.mock("./token", () => ({ freshToken: async () => "t" }));

const console_ = { auth: { token: "t", logout: () => {} }, gwBase: "http://gw", role: "OWNER" } as unknown as Console;

afterEach(() => {
  cleanup();
  enabled = true;
  offered = true;
  access = "ENABLED";
  declineReason = null;
  asked.length = 0;
  withdrawn = 0;
});

describe("AI drafting (ADR-111)", () => {
  it("is not offered when the platform has no model set up", async () => {
    enabled = false;
    const { container } = render(<AiDraftPanel console={console_} onSuggestion={() => {}} />);
    await waitFor(() => expect(container.textContent).toBe(""));
  });

  it("lets an organisation without access ask for it, with a note, and then shows the request waiting", async () => {
    access = "NOT_ENABLED";
    render(<AiDraftPanel console={console_} onSuggestion={() => {}} />);
    expect(screen.queryByText("Describe the purpose")).toBeNull();
    fireEvent.change(await screen.findByLabelText(/Why you would like it/), { target: { value: "We write many purposes" } });
    fireEvent.click(screen.getByText("Ask to turn it on"));
    await screen.findByText(/The platform.s staff have not answered yet/);
    expect(asked).toEqual(["We write many purposes"]);
    fireEvent.click(screen.getByText("Withdraw the request"));
    await screen.findByText("Ask to turn it on");
    expect(withdrawn).toBe(1);
  });

  it("says why a request was turned down, and lets the organisation ask again", async () => {
    access = "DECLINED";
    declineReason = "Not for trial accounts";
    render(<AiDraftPanel console={console_} onSuggestion={() => {}} />);
    expect(await screen.findByText(/turned down: Not for trial accounts/)).toBeTruthy();
    expect(screen.getByText("Ask to turn it on")).toBeTruthy();
  });

  it("offers nothing to ask for when the platform has no model", async () => {
    access = "NOT_ENABLED";
    offered = false;
    const { container } = render(<AiDraftPanel console={console_} onSuggestion={() => {}} />);
    await waitFor(() => expect(container.textContent).toBe(""));
  });

  it("is offered when on, and asks for no personal details", async () => {
    render(<AiDraftPanel console={console_} onSuggestion={() => {}} />);
    fireEvent.click(await screen.findByText("Describe the purpose"));
    expect(screen.getByText(/Do not paste anyone's details/)).toBeTruthy();
    expect((screen.getByText("Draft it") as HTMLButtonElement).disabled).toBe(true);
  });

  it("lists each thing to confirm, and the draft can be saved only once all are ticked", () => {
    const ticks: [number, boolean][] = [];
    render(
      <AiReview
        suggestion={{ code: "X", content: {} as never, confirm: ["Retention", "Recipients"], notes: ["A model wrote this draft."], model: "m", aiGenerated: true }}
        ticked={[false, false]}
        onTick={(i, on) => ticks.push([i, on])}
      />,
    );
    fireEvent.click(screen.getByLabelText(/Retention/));
    expect(ticks).toEqual([[0, true]]);
    expect(screen.getByText("A model wrote this draft.")).toBeTruthy();
    expect(allTicked([true, false], 2)).toBe(false);
    expect(allTicked([true, true], 2)).toBe(true);
    expect(allTicked([], 0)).toBe(true);
  });
});
