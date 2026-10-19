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
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import type { CnsArtRdMdl, CnsReqTermsDto } from "api-client";
import ConsentCard from "./ConsentCard";
import { choiceProblem, dateAfter, daysUntil } from "./Decide";
import type { Portal } from "./portal";

/** Email is needed for the purpose, Name isn't; 30 days asked, up to 90. */
const TERMS: CnsReqTermsDto = {
  cnsReqId: "r1",
  prpsCode: "ORDER_FULFILLMENT",
  prpsVer: 1,
  name: "Order fulfillment",
  description: null,
  dCats: [
    { code: "EMAIL", dpv: null, sensitivity: "ORDINARY", necessary: true, consequence: "We can't confirm your order." },
    { code: "NAME", dpv: null, sensitivity: "ORDINARY", necessary: false, consequence: null },
  ],
  requestedValidityDays: 30,
  maxValidityDays: 90,
  retentionDays: 365,
  recipients: ["Courier Ltd"],
  transfers: [],
  flags: [],
  ntcLang: "en",
  ntcTitle: "Order updates",
  ntcText: "We email you about orders.",
  ntcHash: "h",
};

vi.mock("api-client", async (actual) => ({
  ...(await actual<typeof import("api-client")>()),
  dfReg: { logo: async () => null },
  cnsCmd: { terms: async () => TERMS },
}));

const portal = {
  auth: { token: "t", logout: () => {} },
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

  it("shows the organisation's notice open while the person decides", async () => {
    render(<ConsentCard portal={portal} row={consent({ ntcTitle: "Order updates", ntcText: "We email you about orders." })} />);
    const notice = screen.getByText("Order updates").closest("details")!;
    expect(notice.open).toBe(true);
    expect(screen.getByText("We email you about orders.")).toBeTruthy();
    expect(await screen.findByRole("button", { name: "Give consent" })).toBeTruthy();
  });

  it("ticks no data for the person, and won't give consent to nothing", async () => {
    render(<ConsentCard portal={portal} row={consent({ dCatCodes: ["EMAIL", "NAME"] })} />);
    const boxes = (await screen.findAllByRole("checkbox")) as HTMLInputElement[];
    expect(boxes.map((b) => b.checked)).toEqual([false, false]);
    expect((screen.getByRole("button", { name: "Give consent" }) as HTMLButtonElement).disabled).toBe(true);
    expect((screen.getByRole("button", { name: "Decline" }) as HTMLButtonElement).disabled).toBe(false);
  });

  it("gives consent to what was ticked, for the validity asked unless changed", async () => {
    render(<ConsentCard portal={portal} row={consent({ dCatCodes: ["EMAIL", "NAME"] })} />);
    fireEvent.click((await screen.findAllByRole("checkbox"))[0]);
    fireEvent.click(screen.getByRole("button", { name: "Give consent to 1 of 2" }));
    expect(portal.decide).toHaveBeenCalledWith(expect.objectContaining({ cnsReqId: "r1" }), "give", {
      dCatCodes: ["EMAIL"],
      validityDays: 30,
    });
  });

  it("says what refusing needed data means", async () => {
    render(<ConsentCard portal={portal} row={consent({ dCatCodes: ["EMAIL", "NAME"] })} />);
    const consequence = await screen.findByText(/We can't confirm your order/);
    expect(consequence.className).toContain("dp-consequence-shown");
    fireEvent.click(screen.getAllByRole("checkbox")[0]);
    expect(screen.getByText(/We can't confirm your order/).className).not.toContain("dp-consequence-shown");
  });

  it("shows the data not shared on a consent given in part", () => {
    render(
      <ConsentCard
        portal={portal}
        row={consent({ lcySts: "ISSUED", cnsArtId: "a1", dCatCodes: ["EMAIL", "NAME"], grantedDCatCodes: ["EMAIL"] })}
      />,
    );
    expect(screen.getByText("Name").className).toContain("dp-chip-refused");
    expect(screen.getByText("Email").className).not.toContain("dp-chip-refused");
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

describe("choosing how long consent lasts", () => {
  const today = new Date(2026, 9, 20);

  it("counts whole days between a date and today", () => {
    expect(dateAfter(30, today)).toBe("2026-11-19");
    expect(daysUntil("2026-11-19", today)).toBe(30);
    expect(daysUntil("2026-10-21", today)).toBe(1);
    expect(daysUntil("", today)).toBeNaN();
  });

  it("needs some data and a date from tomorrow up to the purpose's maximum", () => {
    expect(choiceProblem(["EMAIL"], 30, 90)).toBeNull();
    expect(choiceProblem([], 30, 90)).toMatch(/Tick/);
    expect(choiceProblem(["EMAIL"], 0, 90)).toMatch(/after today/);
    expect(choiceProblem(["EMAIL"], 91, 90)).toMatch(/90 days/);
  });
});
