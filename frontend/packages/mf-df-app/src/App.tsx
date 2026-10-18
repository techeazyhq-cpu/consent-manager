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

import { useState } from "react";
import type { AuthInfo } from "auth-kit";
import type { CnsLcySts, DfRole } from "api-client";
import { AppShell, ErrorBanner, Icons, PageHeader, useHashRoute, type NavItem } from "ui-kit/src/index";
import "./console.css";
import { useConsole } from "./console";
import { Account, Dashboard, Organisation, PurposeDetail, Requests } from "./pages";
import Raise from "./Raise";
import Team from "./Team";
import { ROLE_LABELS, managesTeam, raisesRequests } from "./roles";
import { parseRoute } from "./routes";

interface Props {
  auth: AuthInfo;
  dfId: string;
  role: DfRole;
  gwBase: string;
}

/** DF Console: a left menu, a dashboard of the purposes asked for, and a page per purpose (ADR-033 roles). */
export default function DfConsoleApp({ auth, dfId, role, gwBase }: Props) {
  const route = useHashRoute(parseRoute);
  const c = useConsole(auth, gwBase, dfId, role);
  // a status picked on the dashboard, applied when the requests page opens
  const [statusFilter, setStatusFilter] = useState<CnsLcySts[] | undefined>();

  const items: NavItem[] = [
    { key: "dashboard", href: "#/dashboard", label: "Dashboard", section: "Consent", icon: Icons.dashboard },
    ...(raisesRequests(role)
      ? [{ key: "raise", href: "#/raise", label: "Raise a request", section: "Consent", icon: Icons.send }]
      : []),
    { key: "requests", href: "#/requests", label: "Requests", section: "Consent", icon: Icons.list },
    { key: "team", href: "#/team", label: "Team", section: "Organisation", icon: Icons.people },
    { key: "organisation", href: "#/organisation", label: "Organisation", section: "Organisation", icon: Icons.building },
    { key: "account", href: "#/account", label: "Account", section: "Organisation", icon: Icons.user },
  ];
  // a purpose's page belongs under the dashboard in the menu
  const current = route.page === "purpose" ? "dashboard" : route.page;

  return (
    <AppShell
      appName={c.dfName}
      tagline="DF Console"
      mark="DF"
      items={items}
      current={current}
      userName={auth.username ?? auth.email}
      userRole={ROLE_LABELS[role]}
      onSignOut={auth.logout}
    >
      {c.error ? <ErrorBanner message={c.error} /> : null}
      {route.page === "dashboard" ? (
        <Dashboard
          console={c}
          onFilter={(sts) => {
            setStatusFilter(sts);
            window.location.hash = "#/requests";
          }}
        />
      ) : null}
      {route.page === "raise" ? (
        raisesRequests(role) ? (
          <Raise console={c} prpsCode={route.prpsCode} />
        ) : (
          <p className="cm-note">Your role doesn't raise consent requests.</p>
        )
      ) : null}
      {route.page === "requests" ? <Requests key={statusFilter?.join() ?? "all"} console={c} initial={statusFilter} /> : null}
      {route.page === "purpose" ? <PurposeDetail console={c} prpsCode={route.prpsCode} /> : null}
      {route.page === "team" ? (
        <>
          <PageHeader title="Team" subtitle="The people at your organisation who use this console." />
          <Team auth={auth} role={role} gwBase={gwBase} canManage={managesTeam(role)} />
        </>
      ) : null}
      {route.page === "organisation" ? <Organisation console={c} /> : null}
      {route.page === "account" ? <Account console={c} /> : null}
    </AppShell>
  );
}
