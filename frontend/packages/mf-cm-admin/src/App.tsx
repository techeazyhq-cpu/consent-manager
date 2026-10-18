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

import { useMemo } from "react";
import type { AuthInfo } from "auth-kit";
import { AppShell, Icons, useHashRoute, type NavItem } from "ui-kit/src/index";
import Dashboard from "./Dashboard";
import Onboard from "./Onboard";
import TenantDetail from "./TenantDetail";
import Staff from "./Staff";
import Security from "./Security";
import type { Session } from "./session";
import { parseRoute } from "./routes";

interface Props {
  auth: AuthInfo;
  gwBase: string;
}

/** CM Admin: a left menu, the dashboard of tenants by default, and a page per tenant. */
export default function CmAdminApp({ auth, gwBase }: Props) {
  const route = useHashRoute(parseRoute);
  const session = useMemo<Session>(
    () => ({ auth, gwBase, isAdmin: auth.roles.includes("CM_ADMIN") }),
    [auth, gwBase],
  );

  const items: NavItem[] = [
    { key: "dashboard", href: "#/dashboard", label: "Dashboard", section: "Tenants", icon: Icons.dashboard },
    { key: "onboard", href: "#/onboard", label: "Onboard tenant", section: "Tenants", icon: Icons.building },
    // only administrators manage staff
    ...(session.isAdmin
      ? [{ key: "staff", href: "#/staff", label: "Platform staff", section: "Platform", icon: Icons.people }]
      : []),
    { key: "security", href: "#/security", label: "Sign-in & security", section: "Platform", icon: Icons.shield },
  ];
  // A tenant's page belongs under the dashboard in the menu.
  const current = route.page === "tenant" ? "dashboard" : route.page;

  return (
    <AppShell
      appName="CM Admin"
      tagline="Consent Manager platform"
      mark="CM"
      items={items}
      current={current}
      userName={auth.username}
      userRole={session.isAdmin ? "Administrator" : "Operator"}
      onSignOut={auth.logout}
    >
      {route.page === "dashboard" ? <Dashboard session={session} /> : null}
      {route.page === "onboard" ? <Onboard session={session} /> : null}
      {route.page === "tenant" ? <TenantDetail key={route.dfId} session={session} dfId={route.dfId} tab={route.tab} /> : null}
      {route.page === "staff" ? (session.isAdmin ? <Staff session={session} /> : <Dashboard session={session} />) : null}
      {route.page === "security" ? <Security session={session} /> : null}
    </AppShell>
  );
}
