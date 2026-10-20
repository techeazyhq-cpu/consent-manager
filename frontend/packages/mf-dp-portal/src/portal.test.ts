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
import type { CnsArtRdMdl } from "api-client";
import { STATUS, awaitsDecision, humanise } from "./labels";
import { daysLeft, shortenedUntil } from "./Manage";
import { endingSoon } from "./portal";
import { orgHref, parseRoute } from "./routes";

describe("DP Portal routes", () => {
  it("lands on the dashboard by default and for anything unknown", () => {
    expect(parseRoute("")).toEqual({ page: "dashboard" });
    expect(parseRoute("#/elsewhere")).toEqual({ page: "dashboard" });
    expect(parseRoute("#/organisations")).toEqual({ page: "dashboard" });
  });

  it("opens an organisation's page and the menu's pages", () => {
    expect(parseRoute(orgHref("df 1"))).toEqual({ page: "org", dfId: "df 1" });
    for (const page of ["requests", "consents", "notifications", "account"] as const) {
      expect(parseRoute(`#/${page}`)).toEqual({ page });
    }
  });
});

describe("DP Portal wording", () => {
  it("turns codes into words", () => {
    expect(humanise("ORDER_FULFILLMENT")).toBe("Order fulfillment");
    expect(humanise("EMAIL")).toBe("Email");
  });

  it("treats a request it couldn't notify about as still waiting for the person", () => {
    expect(awaitsDecision("NOTIFICATION_FAILED")).toBe(true);
    expect(STATUS.NOTIFICATION_FAILED.label).toBe(STATUS.PENDING.label);
    expect(awaitsDecision("ISSUED")).toBe(false);
    expect(STATUS.REVOKED.label).toBe("Withdrawn");
  });
});

describe("changing a consent in force", () => {
  const now = new Date("2026-10-20T12:00:00Z");
  const row = (cnsReqId: string, lcySts: CnsArtRdMdl["lcySts"], validUntil: string) =>
    ({ cnsReqId, lcySts, validUntil }) as CnsArtRdMdl;

  it("prompts to renew only consents in force that end within two weeks, soonest first", () => {
    const rows = [
      row("later", "ISSUED", "2026-12-01T12:00:00Z"),
      row("soon", "ISSUED", "2026-10-30T12:00:00Z"),
      row("sooner", "ISSUED", "2026-10-22T12:00:00Z"),
      row("withdrawn", "REVOKED", "2026-10-22T12:00:00Z"),
    ];
    expect(endingSoon(rows, now).map((r) => r.cnsReqId)).toEqual(["sooner", "soon"]);
  });

  it("counts the days left, a consent ending later today having one", () => {
    expect(daysLeft("2026-10-20T18:00:00Z", now)).toBe(1);
    expect(daysLeft("2026-10-27T12:00:00Z", now)).toBe(7);
    expect(daysLeft("2026-10-19T12:00:00Z", now)).toBe(0);
  });

  it("ends a consent sooner at the same time of day, whole days earlier", () => {
    const until = new Date(2026, 11, 1, 9, 30).toISOString();
    expect(shortenedUntil(until, "2026-11-24")).toBe(new Date(2026, 10, 24, 9, 30).toISOString());
  });
});
