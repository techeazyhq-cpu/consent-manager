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

import { describe, expect, it } from "vitest";
import type { CnsArtRdMdl, CnsLcySts, CnsReqBatchDto, PrpsContent, PrpsSummaryDto } from "api-client";
import { MAX_FILE_BYTES, fileProblem, progressOf, sendProblem } from "./Batches";
import { dataLabel, grantRate } from "./labels";
import { purposeTiles } from "./console";
import { contentProblem, emptyContent, linesOf, tidy } from "./Purposes";
import { requestProblem, toCode } from "./Raise";
import { batchHref, parseRoute, purposeEditHref, purposeHref, raiseHref } from "./routes";
import { askable, noticeIn } from "./Template";
import { usageNote } from "./Billing";
import { WEBHOOK_TYPES, WEBHOOK_TYPE_LABELS, lastOutcome } from "./Webhooks";
import { managesWebhooks } from "./roles";
import { OPERATIONS, asCurl, fillPath, pathParams, remembered, tokenCurl, uploadForm } from "./sandbox";

describe("DF Console routes", () => {
  it("lands on the dashboard by default and for anything unknown", () => {
    expect(parseRoute("")).toEqual({ page: "dashboard" });
    expect(parseRoute("#/nowhere")).toEqual({ page: "dashboard" });
  });

  it("lists purposes, and opens one, a new one, or its next version's draft", () => {
    expect(parseRoute("#/purposes")).toEqual({ page: "purposes" });
    expect(parseRoute(purposeEditHref())).toEqual({ page: "purpose-edit" });
    expect(parseRoute(purposeEditHref("ORDER_UPDATES"))).toEqual({ page: "purpose-edit", prpsCode: "ORDER_UPDATES" });
  });

  it("opens a purpose, and the request form with or without one", () => {
    expect(parseRoute(purposeHref("ORDER_UPDATES"))).toEqual({ page: "purpose", prpsCode: "ORDER_UPDATES" });
    expect(parseRoute(raiseHref())).toEqual({ page: "raise" });
    expect(parseRoute(raiseHref("ORDER_UPDATES"))).toEqual({ page: "raise", prpsCode: "ORDER_UPDATES" });
    for (const page of ["requests", "team", "organisation", "account"] as const) {
      expect(parseRoute(`#/${page}`)).toEqual({ page });
    }
  });
});

const content = (over: Partial<PrpsContent> = {}): PrpsContent => ({
  ...emptyContent(),
  name: "Order updates",
  dCats: [{ code: "EMAIL", sensitivity: "ORDINARY", necessary: true, consequence: "No updates without it." }],
  notices: [
    { lang: "hi", title: "ऑर्डर अपडेट", text: "हम ईमेल करते हैं।" },
    { lang: "en", title: "Order updates", text: "We email you about your orders." },
  ],
  ...over,
});

const summary = (code: string, over: Partial<PrpsSummaryDto> = {}): PrpsSummaryDto => ({
  id: code,
  code,
  currentVer: 1,
  draftVer: null,
  current: content(),
  draft: null,
  updatedAt: "2026-10-01T00:00:00Z",
  ...over,
});

describe("raising a request", () => {
  it("files what people type under codes", () => {
    expect(toCode(" Order updates! ")).toBe("ORDER_UPDATES");
  });

  it("needs a published purpose version, some of its data, and who to ask", () => {
    const ok = { extDpRefTyp: "EMAIL" as const, extDpRefVal: "a@b.example", prpsCode: "ORDER_UPDATES", prpsVer: 1, dCatCodes: ["EMAIL"] };
    expect(requestProblem(ok)).toBeNull();
    expect(requestProblem({ ...ok, dCatCodes: undefined })).toBeNull();
    expect(requestProblem({ ...ok, prpsCode: "" })).toMatch(/purpose/);
    expect(requestProblem({ ...ok, prpsVer: 0 })).toMatch(/publish/i);
    expect(requestProblem({ ...ok, dCatCodes: [] })).toMatch(/data/);
    expect(requestProblem({ ...ok, extDpRefVal: "" })).toMatch(/who/);
  });

  it("asks only from purposes with a published version, in English unless another language is chosen", () => {
    const defs = [summary("ORDER_UPDATES"), summary("DRAFT_ONLY", { currentVer: null, current: null, draftVer: 1, draft: content() })];
    expect(askable(defs).map((d) => d.code)).toEqual(["ORDER_UPDATES"]);
    expect(askable(null)).toEqual([]);
    expect(noticeIn(content())?.lang).toBe("en");
    expect(noticeIn(content(), "hi")?.lang).toBe("hi");
    expect(noticeIn(content(), "ta")).toBeUndefined();
    expect(noticeIn(content({ notices: [{ lang: "ta", title: "T", text: "X" }] }))?.lang).toBe("ta");
  });
});

describe("purposes", () => {
  it("shows every defined purpose, and any code requests used, with their counts", () => {
    const rows = [
      { prpsCode: "ORDER_UPDATES", lcySts: "ISSUED", createdAt: "2026-10-02T00:00:00Z" },
      { prpsCode: "OLD_CODE", lcySts: "PENDING", createdAt: "2026-09-01T00:00:00Z" },
    ] as CnsArtRdMdl[];
    const tiles = purposeTiles([summary("ORDER_UPDATES"), summary("NEWSLETTER", { updatedAt: "2026-09-15T00:00:00Z" })], rows);
    expect(tiles.map((t) => [t.code, t.sent, t.granted, t.pending])).toEqual([
      ["ORDER_UPDATES", 1, 1, 0],
      ["NEWSLETTER", 0, 0, 0],
      ["OLD_CODE", 1, 0, 1],
    ]);
    expect(tiles[0].name).toBe("Order updates");
    expect(tiles[2].currentVer).toBeNull();
  });

  it("tidies a draft before it is checked: trimmed codes, blank rows and empty optional fields dropped", () => {
    const out = tidy(
      content({
        name: " Order updates ",
        description: "  ",
        dCats: [
          { code: " e-mail ", dpv: " ", sensitivity: "ORDINARY", necessary: false, consequence: "" },
          { code: " ", sensitivity: "ORDINARY", necessary: false },
        ],
        notices: [
          { lang: " en ", title: " T ", text: " X " },
          { lang: "hi", title: "", text: "" },
        ],
      }),
    );
    expect(out.name).toBe("Order updates");
    expect(out.description).toBeNull();
    expect(out.dCats).toEqual([{ code: "E_MAIL", dpv: null, sensitivity: "ORDINARY", necessary: false, consequence: null }]);
    expect(out.notices).toEqual([{ lang: "en", title: "T", text: "X" }]);
    expect(linesOf(" Courier Ltd \n\n Ad Co ")).toEqual(["Courier Ltd", "Ad Co"]);
  });

  it("says what to fix first: a name, data, a consequence for needed data, sane validity and a full notice", () => {
    expect(contentProblem(content())).toBeNull();
    expect(contentProblem(content({ name: "" }))).toMatch(/name/);
    expect(contentProblem(content({ dCats: [] }))).toMatch(/data/);
    expect(contentProblem(content({ dCats: [{ code: "EMAIL", sensitivity: "ORDINARY", necessary: true }] }))).toMatch(/refuses/);
    expect(contentProblem(content({ requestedValidityDays: 400, maxValidityDays: 365 }))).toMatch(/validity/);
    expect(contentProblem(content({ notices: [{ lang: "en", title: "", text: "X" }] }))).toMatch(/notice/);
  });
});

describe("what a consent covers", () => {
  const word = (c: string) => c.toLowerCase();

  it("is what was asked until given, then what was granted, naming what wasn't", () => {
    expect(dataLabel({ dCatCodes: ["EMAIL", "NAME"], grantedDCatCodes: null }, word)).toBe("email, name");
    expect(dataLabel({ dCatCodes: ["EMAIL", "NAME"], grantedDCatCodes: ["EMAIL", "NAME"] }, word)).toBe("email, name");
    expect(dataLabel({ dCatCodes: ["EMAIL", "NAME"], grantedDCatCodes: ["EMAIL"] }, word)).toBe("email (not given: name)");
  });
});

describe("grant rate", () => {
  const rows = (...sts: CnsLcySts[]) => sts.map((lcySts) => ({ lcySts }) as CnsArtRdMdl);

  it("counts only people who decided, and withdrawals as grants first", () => {
    expect(grantRate(rows("PENDING", "NOTIFICATION_FAILED"))).toBeNull();
    expect(grantRate(rows("ISSUED", "DENIED"))).toBe(50);
    expect(grantRate(rows("ISSUED", "REVOKED", "EXPIRED", "DENIED", "PENDING"))).toBe(75);
  });
});

describe("sending to many", () => {
  const counts = { valid: 10, sent: 0, failed: 0 };

  it("routes to the new list, the lists and one list", () => {
    expect(parseRoute("#/send-to-many")).toEqual({ page: "send-to-many" });
    expect(parseRoute("#/batches")).toEqual({ page: "batches" });
    expect(parseRoute(batchHref("b-1"))).toEqual({ page: "batch", id: "b-1" });
  });

  it("routes to the webhooks", () => {
    expect(parseRoute("#/webhooks")).toEqual({ page: "webhooks" });
  });

  it("takes CSV and Excel files up to 5 MB", () => {
    expect(fileProblem(null)).toMatch(/Choose/);
    expect(fileProblem(new File(["x"], "list.CSV"))).toBeNull();
    expect(fileProblem(new File(["x"], "list.xlsx"))).toBeNull();
    expect(fileProblem(new File(["x"], "list.pdf"))).toMatch(/CSV/);
    expect(fileProblem(new File([new Uint8Array(MAX_FILE_BYTES + 1)], "big.csv"))).toMatch(/5 MB/);
  });

  it("counts sent and failed rows as handled", () => {
    expect(progressOf(counts)).toBe(0);
    expect(progressOf({ ...counts, sent: 6, failed: 1 })).toBe(70);
    expect(progressOf({ valid: 0, sent: 0, failed: 0 })).toBe(100);
  });

  it("won't send a list with no one on it, or more than the plan has left", () => {
    const batch = { valid: 10 } as CnsReqBatchDto;
    expect(sendProblem({ batch, available: 10 })).toBeNull();
    expect(sendProblem({ batch, available: 9 })).toMatch(/10 people.*9 requests left/);
    expect(sendProblem({ batch: { valid: 0 } as CnsReqBatchDto, available: 50 })).toMatch(/No one/);
  });
});

describe("the API playground", () => {
  const op = (id: string) => OPERATIONS.find((o) => o.id === id)!;

  it("has its own page", () => {
    expect(parseRoute("#/playground")).toEqual({ page: "playground" });
  });

  it("fills path placeholders, and waits until each has a value", () => {
    expect(pathParams("/api/cns-cmd/cns-req-batches/{batchId}/rows")).toEqual(["batchId"]);
    expect(fillPath("/a/{batchId}/rows", {})).toBeNull();
    expect(fillPath("/a/{batchId}/rows", { batchId: " b 1 " })).toBe("/a/b%201/rows");
  });

  it("remembers the ids later calls need", () => {
    expect(remembered(op("batch"), { batch: { id: "b-9" } })).toEqual({ batchId: "b-9" });
    expect(remembered(op("list"), [{ cnsReqId: "r-1" }])).toEqual({ requestId: "r-1" });
    expect(remembered(op("list"), [])).toEqual({});
    expect(remembered(op("jwks"), { keys: [] })).toEqual({});
  });

  it("asks for the purpose version its first samples define and publish", () => {
    expect(OPERATIONS.slice(0, 2).map((o) => o.id)).toEqual(["purpose", "purpose-publish"]);
    expect(remembered(op("purpose"), { code: "ORDER_UPDATES" })).toEqual({ prpsCode: "ORDER_UPDATES" });
    const raise = op("raise").body;
    expect(raise.kind === "json" && JSON.parse(raise.sample)).toMatchObject({ prpsCode: "ORDER_UPDATES", prpsVer: 1 });
  });

  it("offers only test contacts in its samples", () => {
    for (const o of OPERATIONS) {
      if (o.body.kind === "none") continue;
      for (const contact of o.body.sample.match(/[\w.+-]+@[\w.-]+/g) ?? []) expect(contact).toMatch(/@sandbox\.example$/);
      for (const phone of o.body.sample.match(/\+\d{8,15}/g) ?? []) expect(phone).toMatch(/^\+447700900\d{3}$/);
    }
  });

  it("copies calls as curl without the token or the secret", () => {
    const curl = asCurl(op("raise"), "/api/cns-cmd/cns-reqs", `{"note":"it's"}`);
    expect(curl).toContain("http://localhost:9100/api/cns-cmd/cns-reqs");
    expect(curl).toContain('"Authorization: Bearer $SANDBOX_TOKEN"');
    expect(curl).toContain(`--data-raw '{"note":"it'\\''s"}'`);
    expect(asCurl(op("jwks"), "/api/cns-cmd/.well-known/jwks.json", "")).not.toContain("Authorization");
    expect(tokenCurl({ clientId: "df-api-1", tokenUrl: "http://kc/token" })).toContain('"client_secret=$SANDBOX_CLIENT_SECRET"');
  });

  it("sends an upload as form fields and a CSV file", async () => {
    const form = uploadForm(JSON.stringify({ prpsCode: "P", prpsVer: 2, dCatCodes: ["EMAIL", "NAME"], file: "Email\na@sandbox.example\n" }));
    expect(form.get("prpsCode")).toBe("P");
    expect(form.get("prpsVer")).toBe("2");
    expect(form.get("dCatCodes")).toBe("EMAIL,NAME");
    const file = form.get("file") as File;
    expect(file.name).toBe("list.csv");
    // jsdom's File has no text(); FileReader reads it the same way
    const text = await new Promise<string>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result));
      reader.readAsText(file);
    });
    expect(text).toBe("Email\na@sandbox.example\n");
  });
});

describe("billing", () => {
  const resetsAt = "2026-11-01T00:00:00Z";

  it("has its own page", () => {
    expect(parseRoute("#/billing")).toEqual({ page: "billing" });
  });

  it("says how much is left, warns from 80%, and says when a used-up allowance resets", () => {
    expect(usageNote({ curPeriodUsg: 100, reqPerMonthQta: 1000, resetsAt })).toMatch(/^900 left this month\. It resets/);
    expect(usageNote({ curPeriodUsg: 850, reqPerMonthQta: 1000, resetsAt })).toMatch(/close to your allowance/);
    expect(usageNote({ curPeriodUsg: 1200, reqPerMonthQta: 1000, resetsAt })).toMatch(/used up: new requests are refused/);
  });
});

describe("webhooks", () => {
  it("says how a delivery last went", () => {
    expect(lastOutcome({ attempts: 0, lastStatus: null, lastError: null })).toBe("Not tried yet");
    expect(lastOutcome({ attempts: 2, lastStatus: 503, lastError: null })).toBe("HTTP 503");
    expect(lastOutcome({ attempts: 1, lastStatus: null, lastError: "timeout" })).toBe("timeout");
  });

  it("offers every type the API sends", () => {
    expect(WEBHOOK_TYPES).toEqual(["consent.requested", "consent.notification_failed", "consent.given", "consent.refused",
      "consent.modified", "consent.renewed", "consent.withdrawn", "consent.lapsed", "receipt.issued"]);
  });

  it("labels every message type", () => {
    for (const t of ["consent.given", "consent.withdrawn", "consent.lapsed", "receipt.issued", "webhook.test"]) {
      expect(WEBHOOK_TYPE_LABELS[t]).toBeTruthy();
    }
  });

  it("lets the people who build integrations manage them", () => {
    expect(managesWebhooks("DEVELOPER")).toBe(true);
    expect(managesWebhooks("CONSENT_MANAGER")).toBe(false);
    expect(managesWebhooks("AUDITOR")).toBe(false);
  });
});
