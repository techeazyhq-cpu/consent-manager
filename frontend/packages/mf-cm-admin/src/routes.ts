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

/** CM Admin's pages, addressed by the URL hash so each can be bookmarked, reloaded and reached with Back. */
export const TENANT_TABS = ["overview", "subscription", "consents", "team"] as const;
export type TenantTab = (typeof TENANT_TABS)[number];

export const BILLING_TABS = ["overview", "requests", "plans"] as const;
export type BillingTab = (typeof BILLING_TABS)[number];

export type Route =
  | { page: "dashboard" }
  | { page: "onboard" }
  | { page: "staff" }
  | { page: "security" }
  | { page: "billing"; tab: BillingTab }
  | { page: "tenant"; dfId: string; tab: TenantTab };

/** Anything unrecognised lands on the dashboard, the default page. */
export function parseRoute(hash: string): Route {
  const parts = hash.replace(/^#\/?/, "").split("/").filter(Boolean).map(decodeURIComponent);
  switch (parts[0]) {
    case "onboard":
    case "staff":
    case "security":
      return { page: parts[0] };
    case "billing":
      return {
        page: "billing",
        tab: (BILLING_TABS as readonly string[]).includes(parts[1]) ? (parts[1] as BillingTab) : "overview",
      };
    case "tenants":
      if (parts[1]) {
        const tab = (TENANT_TABS as readonly string[]).includes(parts[2]) ? (parts[2] as TenantTab) : "overview";
        return { page: "tenant", dfId: parts[1], tab };
      }
      return { page: "dashboard" };
    default:
      return { page: "dashboard" };
  }
}

export function tenantHref(dfId: string, tab: TenantTab = "overview"): string {
  return `#/tenants/${encodeURIComponent(dfId)}${tab === "overview" ? "" : `/${tab}`}`;
}

export const billingHref = (tab: BillingTab = "overview") => (tab === "overview" ? "#/billing" : `#/billing/${tab}`);
