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
import type { DpReqDto } from "api-client";
import { dueLabel } from "ui-kit/src/requests";
import { escalation, needsDetails } from "./Rights";
import { parseRoute, rightsHref } from "./routes";

const req = (over: Partial<DpReqDto>): DpReqDto => ({
  id: "r-1",
  kind: "RELAY",
  dfId: "df-1",
  cnsReqId: "cns-1",
  prpsCode: "MARKETING",
  right: "ACCESS",
  details: null,
  sts: "OPEN",
  receivedAt: "2026-11-01T00:00:00Z",
  dueBy: "2026-11-11T00:00:00Z",
  overdue: false,
  acknowledgedAt: null,
  closedAt: null,
  answeredBy: null,
  response: null,
  refusalGround: null,
  ...over,
});

describe("your rights (ADR-058)", () => {
  it("opens with a consent picked from its history", () => {
    expect(parseRoute(rightsHref("cns 1"))).toEqual({ page: "rights", cnsReqId: "cns 1" });
    expect(parseRoute("#/rights")).toEqual({ page: "rights" });
  });

  it("asks for details only where the organisation needs them", () => {
    expect(needsDetails("ACCESS")).toBe(false);
    expect(needsDetails("ERASURE")).toBe(false);
    expect(needsDetails("CORRECTION")).toBe(true);
    expect(needsDetails("NOMINATION")).toBe(true);
  });

  it("points to the Board once the answer is in or late, and the organisation's own officer first", () => {
    expect(escalation(req({}))).toBeNull();
    expect(escalation(req({ sts: "REFUSED" }))).toMatch(/grievance officer first/);
    expect(escalation(req({ overdue: true }))).toMatch(/Data Protection Board/);
    expect(escalation(req({ kind: "GRIEVANCE", sts: "RESOLVED" }))).not.toMatch(/grievance officer/);
    expect(escalation(req({ sts: "WITHDRAWN" }))).toBeNull();
  });

  it("says how long is left, or how late it is", () => {
    const now = new Date("2026-11-07T00:00:00Z").getTime();
    expect(dueLabel(req({}), now)).toBe("Due in 4 days");
    expect(dueLabel(req({ dueBy: "2026-11-05T00:00:00Z", overdue: true }), now)).toBe("Overdue by 2 days");
    expect(dueLabel(req({ sts: "RESOLVED" }), now)).toBe("");
  });
});
