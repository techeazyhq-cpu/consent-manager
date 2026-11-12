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
import type { AiDraftingUpdate, AiDraftingViewDto } from "api-client";
import AiDrafting, { isOwnHost, leavesPlatform } from "./AiDrafting";
import type { Session } from "./session";

const providers: AiDraftingViewDto["providers"] = [
  { id: "OLLAMA", label: "Ollama", defaultEndpoint: "http://localhost:11434", defaultModel: "llama3.2:3b", endpointEditable: true, keyRequired: false, thirdParty: false },
  { id: "ANTHROPIC", label: "Anthropic", defaultEndpoint: "https://api.anthropic.com", defaultModel: "claude-haiku-5-5", endpointEditable: false, keyRequired: true, thirdParty: true },
  { id: "OPENAI", label: "OpenAI", defaultEndpoint: "https://api.openai.com", defaultModel: "gpt-4o-mini", endpointEditable: false, keyRequired: true, thirdParty: true },
];

const saves: AiDraftingUpdate[] = [];
const tests: AiDraftingUpdate[] = [];
let current: AiDraftingViewDto;

const base = (over: Partial<AiDraftingViewDto["config"]> = {}): AiDraftingViewDto => ({
  fromProperties: false,
  providers,
  config: {
    enabled: false,
    provider: "OLLAMA",
    endpoint: "http://localhost:11434",
    model: "llama3.2:3b",
    apiKeySet: false,
    timeoutSeconds: 100,
    maxTokens: 1500,
    thirdPartyAck: false,
    leavesPlatform: false,
    updatedBy: null,
    updatedAt: null,
    ...over,
  },
});

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  dfReg: { list: async () => [] },
  aiDraftingConfig: {
    accessList: async () => ({ pending: [], enabled: [] }),
    get: async () => current,
    save: async (_b: string, _t: string, u: AiDraftingUpdate) => {
      saves.push(u);
      current = base({ enabled: u.enabled, provider: u.provider, endpoint: u.endpoint, model: u.model, apiKeySet: !!u.apiKey, thirdPartyAck: u.thirdPartyAck });
      return current;
    },
    test: async (_b: string, _t: string, u: AiDraftingUpdate) => {
      tests.push(u);
      return { ok: true, millis: 120, message: "The model answered." };
    },
  },
}));
vi.mock("./session", () => ({ freshToken: async () => "t" }));

const session = (isAdmin: boolean) => ({ auth: { token: "t", logout: () => {} }, gwBase: "http://gw", isAdmin }) as unknown as Session;

afterEach(() => {
  cleanup();
  saves.length = 0;
  tests.length = 0;
});

describe("AI drafting model page (ADR-113)", () => {
  it("knows which addresses are the platform's own", () => {
    for (const h of ["localhost", "127.0.0.1", "10.2.3.4", "192.168.1.5", "172.20.0.1", "vllm", "vllm.models.svc", "[::1]"]) expect(isOwnHost(h), h).toBe(true);
    for (const h of ["models.example.com", "172.40.0.1", "8.8.8.8"]) expect(isOwnHost(h), h).toBe(false);
    expect(leavesPlatform(providers[1], "")).toBe(true);
    expect(leavesPlatform(providers[0], "http://localhost:11434")).toBe(false);
    expect(leavesPlatform(providers[0], "https://models.example.com")).toBe(true);
  });

  it("lists every provider the platform supports", async () => {
    current = base();
    render(<AiDrafting session={session(true)} />);
    const select = (await screen.findByLabelText("Provider")) as HTMLSelectElement;
    expect([...select.options].map((o) => o.value)).toEqual(["OLLAMA", "ANTHROPIC", "OPENAI"]);
  });

  it("will not save a vendor until the administrator has said descriptions may go there", async () => {
    current = base();
    render(<AiDrafting session={session(true)} />);
    fireEvent.change(await screen.findByLabelText("Provider"), { target: { value: "ANTHROPIC" } });
    fireEvent.click(screen.getByLabelText(/Offer drafting with a model/));
    fireEvent.change(screen.getByLabelText("API key"), { target: { value: "sk-ant-test" } });
    expect(screen.getByText(/sends fiduciaries.? descriptions outside the platform/i)).toBeTruthy();
    const save = screen.getByRole("button", { name: "Save" }) as HTMLButtonElement;
    expect(save.disabled).toBe(true);
    fireEvent.click(screen.getByLabelText(/I choose to send them there/));
    expect(save.disabled).toBe(false);
    fireEvent.click(save);
    await waitFor(() => expect(saves).toHaveLength(1));
    expect(saves[0]).toMatchObject({ provider: "ANTHROPIC", model: "claude-haiku-5-5", apiKey: "sk-ant-test", thirdPartyAck: true, enabled: true });
  });

  it("never shows a stored key, and keeps it when the field is left blank", async () => {
    current = base({ enabled: true, provider: "ANTHROPIC", endpoint: "https://api.anthropic.com", model: "claude-haiku-5-5", apiKeySet: true, thirdPartyAck: true, leavesPlatform: true });
    render(<AiDrafting session={session(true)} />);
    const key = (await screen.findByLabelText("API key")) as HTMLInputElement;
    expect(key.value).toBe("");
    expect(key.placeholder).toMatch(/stored/);
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(saves).toHaveLength(1));
    expect(saves[0].apiKey).toBeUndefined();
    expect(saves[0].clearKey).toBeUndefined();
  });

  it("does not offer to keep a key once the provider changes: the new vendor needs its own", async () => {
    current = base({ enabled: true, provider: "ANTHROPIC", endpoint: "https://api.anthropic.com", model: "claude-haiku-5-5", apiKeySet: true, thirdPartyAck: true, leavesPlatform: true });
    render(<AiDrafting session={session(true)} />);
    fireEvent.change(await screen.findByLabelText("Provider"), { target: { value: "OPENAI" } });
    expect(((await screen.findByLabelText("API key")) as HTMLInputElement).placeholder).toBe("");
    expect(screen.queryByLabelText("Remove the stored key")).toBeNull();
    expect((screen.getByRole("button", { name: "Save" }) as HTMLButtonElement).disabled).toBe(true);
  });

  it("tests the setting as it stands in the form, and says it sends no description", async () => {
    current = base();
    render(<AiDrafting session={session(true)} />);
    fireEvent.change(await screen.findByLabelText("Model"), { target: { value: "qwen2.5:7b" } });
    fireEvent.click(screen.getByRole("button", { name: "Test the connection" }));
    expect(await screen.findByText(/Working: The model answered\. \(120 ms\)/)).toBeTruthy();
    expect(tests[0].model).toBe("qwen2.5:7b");
    expect(saves).toHaveLength(0);
    expect(screen.getByText(/never anyone.s description/)).toBeTruthy();
  });

  it("lets an operator look but not change", async () => {
    current = base();
    render(<AiDrafting session={session(false)} />);
    await screen.findByText(/only a platform administrator changes it/);
    expect(screen.queryByRole("button", { name: "Save" })).toBeNull();
    expect(screen.getByLabelText("Model").matches(":disabled")).toBe(true); // inside a disabled fieldset
  });
});
