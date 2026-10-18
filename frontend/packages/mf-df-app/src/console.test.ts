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
import type { CnsArtRdMdl, CnsLcySts, CnsReqBatchDto } from "api-client";
import { MAX_FILE_BYTES, fileProblem, progressOf, sendProblem } from "./Batches";
import { grantRate } from "./labels";
import { parseCategories, requestProblem, toCode } from "./Raise";
import { batchHref, parseRoute, purposeHref, raiseHref } from "./routes";
import { OPERATIONS, asCurl, fillPath, pathParams, remembered, tokenCurl, uploadForm } from "./sandbox";

describe("DF Console routes", () => {
  it("lands on the dashboard by default and for anything unknown", () => {
    expect(parseRoute("")).toEqual({ page: "dashboard" });
    expect(parseRoute("#/nowhere")).toEqual({ page: "dashboard" });
    expect(parseRoute("#/purposes")).toEqual({ page: "dashboard" });
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

describe("raising a request", () => {
  it("files what people type under codes", () => {
    expect(toCode(" Order updates! ")).toBe("ORDER_UPDATES");
    expect(parseCategories("Email, name,, email, Phone number")).toEqual(["EMAIL", "NAME", "PHONE_NUMBER"]);
  });

  it("needs a purpose, data, a full notice and who to ask", () => {
    const ok = {
      extDpRefTyp: "EMAIL" as const,
      extDpRefVal: "a@b.example",
      prpsCode: "ORDER_UPDATES",
      dCatCodes: ["EMAIL"],
      ntcTitle: "Order updates",
      ntcText: "We email you about your orders. You can withdraw at any time.",
    };
    expect(requestProblem(ok)).toBeNull();
    expect(requestProblem({ ...ok, prpsCode: "" })).toMatch(/for/);
    expect(requestProblem({ ...ok, dCatCodes: [] })).toMatch(/data/);
    expect(requestProblem({ ...ok, ntcTitle: " " })).toMatch(/title/);
    expect(requestProblem({ ...ok, ntcText: "" })).toMatch(/notice/i);
    expect(requestProblem({ ...ok, extDpRefVal: "" })).toMatch(/who/);
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
    const form = uploadForm(JSON.stringify({ prpsCode: "P", dCatCodes: ["EMAIL", "NAME"], file: "Email\na@sandbox.example\n" }));
    expect(form.get("prpsCode")).toBe("P");
    expect(form.get("dCatCodes")).toBe("EMAIL,NAME");
    const file = form.get("file") as File;
    expect(file.name).toBe("list.csv");
    expect(await file.text()).toBe("Email\na@sandbox.example\n");
  });
});
