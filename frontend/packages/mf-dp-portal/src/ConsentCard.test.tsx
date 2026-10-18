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
import { cleanup, render, screen } from "@testing-library/react";
import type { CnsArtRdMdl } from "api-client";
import ConsentCard from "./ConsentCard";
import type { Portal } from "./portal";

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  dfReg: { logo: async () => null },
}));

const portal = {
  auth: { token: "t" },
  gwBase: "",
  busy: null,
  orgName: () => "Acme Retail",
  decide: vi.fn(),
} as unknown as Portal;

const consent = (over: Partial<CnsArtRdMdl>): CnsArtRdMdl => ({
  cnsReqId: "r1",
  cnsArtId: null,
  dfId: "df-1",
  dpId: "dp-1",
  prpsCode: "ORDER_FULFILLMENT",
  dCatCodes: ["EMAIL"],
  lcySts: "PENDING",
  validFrom: null,
  validUntil: null,
  createdAt: "2026-10-18T00:00:00Z",
  updatedAt: "2026-10-18T00:00:00Z",
  ...over,
});

describe("ConsentCard", () => {
  afterEach(cleanup);

  it("shows the organisation's notice open while the person decides", () => {
    render(<ConsentCard portal={portal} row={consent({ ntcTitle: "Order updates", ntcText: "We email you about orders." })} />);
    const notice = screen.getByText("Order updates").closest("details")!;
    expect(notice.open).toBe(true);
    expect(screen.getByText("We email you about orders.")).toBeTruthy();
    expect(screen.getByRole("button", { name: "Give consent" })).toBeTruthy();
  });

  it("folds the notice away once consent is given, still a click from view", () => {
    render(
      <ConsentCard
        portal={portal}
        row={consent({ lcySts: "ISSUED", cnsArtId: "a1", ntcTitle: "Order updates", ntcText: "We email you about orders." })}
      />,
    );
    expect(screen.getByText("Order updates").closest("details")!.open).toBe(false);
    expect(screen.getByRole("button", { name: "Withdraw consent" })).toBeTruthy();
  });

  it("says so when a request came without a notice", () => {
    render(<ConsentCard portal={portal} row={consent({})} />);
    expect(screen.getByText(/without a notice/)).toBeTruthy();
  });

  it("shows notice text as text, never as markup", () => {
    const { container } = render(
      <ConsentCard portal={portal} row={consent({ ntcTitle: "T", ntcText: "<img src=x onerror=alert(1)>" })} />,
    );
    expect(container.querySelector("details img")).toBeNull();
    expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeTruthy();
  });
});
