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

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  prps: {
    aiStatus: async () => ({ enabled, model: "llama3.2:3b", minChars: 20, maxChars: 2000 }),
    aiDraft: async () => ({}),
  },
}));
vi.mock("./token", () => ({ freshToken: async () => "t" }));

const console_ = { auth: { token: "t", logout: () => {} }, gwBase: "http://gw", role: "OWNER" } as unknown as Console;

afterEach(() => {
  cleanup();
  enabled = true;
});

describe("AI drafting (ADR-111)", () => {
  it("is not offered when the platform has no model set up", async () => {
    enabled = false;
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
