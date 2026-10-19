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
import { assignableBy, managesTeam, memberRoleOf, raisesRequests, readsAudit, usesSandbox } from "./roles";

describe("fiduciary member roles", () => {
  it("reads the most powerful member role from a token's realm roles", () => {
    expect(memberRoleOf(["DF", "DF_AUDITOR", "DF_ADMIN"])).toBe("ADMIN");
    expect(memberRoleOf(["DF"])).toBeUndefined();
    expect(memberRoleOf(["CM_ADMIN"])).toBeUndefined();
  });

  it("lets only Owners and Administrators manage the team, and not Developers or Auditors raise requests", () => {
    expect(managesTeam("OWNER") && managesTeam("ADMIN")).toBe(true);
    expect(managesTeam("CONSENT_MANAGER")).toBe(false);
    expect(raisesRequests("CONSENT_MANAGER")).toBe(true);
    expect(raisesRequests("DEVELOPER") || raisesRequests("AUDITOR")).toBe(false);
  });

  it("lets only an Owner give ownership", () => {
    expect(assignableBy("OWNER")).toContain("OWNER");
    expect(assignableBy("ADMIN")).not.toContain("OWNER");
  });

  it("lets Owners, Administrators and Auditors read the audit (ADR-040)", () => {
    expect(["OWNER", "ADMIN", "AUDITOR"].every((r) => readsAudit(r as never))).toBe(true);
    expect(["CONSENT_MANAGER", "DEVELOPER"].some((r) => readsAudit(r as never))).toBe(false);
    expect(usesSandbox("DEVELOPER")).toBe(true);
  });
});
