import { describe, expect, it } from "vitest";
import { assignableBy, managesTeam, memberRoleOf, raisesRequests } from "./roles";

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
});
