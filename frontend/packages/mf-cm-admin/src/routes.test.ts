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
import { billingHref, parseRoute, tenantHref } from "./routes";

describe("CM Admin routes", () => {
  it("lands on the dashboard by default and for anything unknown", () => {
    expect(parseRoute("")).toEqual({ page: "dashboard" });
    expect(parseRoute("#/nowhere")).toEqual({ page: "dashboard" });
    expect(parseRoute("#/tenants")).toEqual({ page: "dashboard" });
  });

  it("opens a tenant on its overview, or the tab named", () => {
    expect(parseRoute("#/tenants/df-1")).toEqual({ page: "tenant", dfId: "df-1", tab: "overview" });
    expect(parseRoute(tenantHref("df-1", "consents"))).toEqual({ page: "tenant", dfId: "df-1", tab: "consents" });
    expect(parseRoute("#/tenants/df-1/bogus")).toEqual({ page: "tenant", dfId: "df-1", tab: "overview" });
  });

  it("reaches the menu's other pages", () => {
    expect(parseRoute("#/onboard").page).toBe("onboard");
    expect(parseRoute("#/staff").page).toBe("staff");
    expect(parseRoute("#/security").page).toBe("security");
    expect(parseRoute("#/audit").page).toBe("audit");
    expect(parseRoute(tenantHref("df-1", "audit"))).toEqual({ page: "tenant", dfId: "df-1", tab: "audit" });
  });

  it("opens billing on its overview, or the tab named", () => {
    expect(parseRoute(billingHref())).toEqual({ page: "billing", tab: "overview" });
    expect(parseRoute(billingHref("requests"))).toEqual({ page: "billing", tab: "requests" });
    expect(parseRoute("#/billing/plans")).toEqual({ page: "billing", tab: "plans" });
    expect(parseRoute("#/billing/bogus")).toEqual({ page: "billing", tab: "overview" });
  });

  it("opens billing on its overview, or the tab named", () => {
    expect(parseRoute(billingHref())).toEqual({ page: "billing", tab: "overview" });
    expect(parseRoute(billingHref("requests"))).toEqual({ page: "billing", tab: "requests" });
    expect(parseRoute("#/billing/plans")).toEqual({ page: "billing", tab: "plans" });
    expect(parseRoute("#/billing/bogus")).toEqual({ page: "billing", tab: "overview" });
  });
});
