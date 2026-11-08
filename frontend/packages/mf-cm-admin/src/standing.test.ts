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
import { canProposeCancel, canProposeEnd, canResume, canSuspend, mayDecide, standingLine } from "./standing";

describe("standing", () => {
  it("only an active tenant can be suspended and only a suspended one resumed", () => {
    expect(canSuspend("ACTIVE")).toBe(true);
    for (const sts of ["PENDING", "SUSPENDED", "LEAVING", "CLOSED"] as const) expect(canSuspend(sts)).toBe(false);
    expect(canResume("SUSPENDED")).toBe(true);
    for (const sts of ["PENDING", "ACTIVE", "LEAVING", "CLOSED"] as const) expect(canResume(sts)).toBe(false);
  });

  it("describes each standing", () => {
    expect(standingLine({ sts: "ACTIVE" })).toBe("Active");
    expect(standingLine({ sts: "SUSPENDED", suspensionReason: "Fraud case 7", suspendedAt: "2026-11-08T00:00:00Z" })).toContain("Fraud case 7");
    expect(standingLine({ sts: "LEAVING", leavesAt: "2026-12-08T00:00:00Z" })).toContain("closes");
    expect(standingLine({ sts: "CLOSED", closedAt: "2026-12-08T00:00:00Z" })).toContain("retention");
    expect(standingLine({ sts: "CLOSED", closedAt: "2026-12-08T00:00:00Z", purgedAt: "2033-12-08T00:00:00Z" })).toContain("purged");
  });

  it("ending a contract is proposed for active or suspended tenants and called off for leaving ones", () => {
    expect(canProposeEnd("ACTIVE") && canProposeEnd("SUSPENDED")).toBe(true);
    for (const sts of ["PENDING", "LEAVING", "CLOSED"] as const) expect(canProposeEnd(sts)).toBe(false);
    expect(canProposeCancel("LEAVING")).toBe(true);
    for (const sts of ["PENDING", "ACTIVE", "SUSPENDED", "CLOSED"] as const) expect(canProposeCancel(sts)).toBe(false);
  });

  it("only an administrator other than the proposer decides", () => {
    expect(mayDecide("alice", "bob", ["CM_ADMIN"])).toBe(true);
    expect(mayDecide("alice", "alice", ["CM_ADMIN"])).toBe(false);
    expect(mayDecide("alice", "bob", ["CM_OPERATOR"])).toBe(false);
  });
});
