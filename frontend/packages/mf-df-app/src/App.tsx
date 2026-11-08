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

import { useEffect, useState } from "react";
import type { AuthInfo } from "auth-kit";
import { leaving, type CnsLcySts, type DfRole, type LeaveStatusDto } from "api-client";
import { AppShell, ErrorBanner, Icons, PageHeader, useHashRoute, type NavItem } from "ui-kit/src/index";
import "./console.css";
import { useConsole } from "./console";
import { BatchDetail, Batches, SendToMany } from "./Batches";
import Agreements from "./Agreements";
import ApiKeys from "./ApiKeys";
import Webhooks from "./Webhooks";
import DevDocs from "./DevDocs";
import Audit from "./Audit";
import Billing from "./Billing";
import PrincipalRequests from "./PrincipalRequests";
import { Account, Dashboard, Organisation, Requests } from "./pages";
import { PurposeDetail, PurposeEditor, Purposes } from "./Purposes";
import Playground from "./Playground";
import Raise from "./Raise";
import Team from "./Team";
import { ROLE_LABELS, managesApiKeys, managesTeam, raisesRequests, readsAudit } from "./roles";
import { parseRoute } from "./routes";
import { standingNotice } from "./standing";
import { freshToken } from "./token";

interface Props {
  auth: AuthInfo;
  dfId: string;
  role: DfRole;
  gwBase: string;
}

/** DF Console: a left menu, a dashboard of the purposes asked for, purposes and their versions (ADR-031, ADR-033 roles). */
export default function DfConsoleApp({ auth, dfId, role, gwBase }: Props) {
  const route = useHashRoute(parseRoute);
  const c = useConsole(auth, gwBase, dfId, role);
  // a status picked on the dashboard, applied when the requests page opens
  const [statusFilter, setStatusFilter] = useState<CnsLcySts[] | undefined>();
  // an organisation that is suspended, leaving or closed says so on every page (ADR-064)
  const [standing, setStanding] = useState<LeaveStatusDto | null>(null);
  useEffect(() => {
    freshToken(auth)
      .then((token) => leaving.status(gwBase, token))
      .then(setStanding)
      .catch(() => setStanding(null));
  }, [auth, gwBase, route.page]);

  const items: NavItem[] = [
    { key: "dashboard", href: "#/dashboard", label: "Dashboard", section: "Consent", icon: Icons.dashboard },
    { key: "purposes", href: "#/purposes", label: "Purposes", section: "Consent", icon: Icons.list },
    ...(raisesRequests(role)
      ? [
          { key: "raise", href: "#/raise", label: "Raise a request", section: "Consent", icon: Icons.send },
          { key: "send-to-many", href: "#/send-to-many", label: "Send to many", section: "Consent", icon: Icons.upload },
        ]
      : []),
    { key: "requests", href: "#/requests", label: "Requests", section: "Consent", icon: Icons.list },
    { key: "batches", href: "#/batches", label: "Lists sent", section: "Consent", icon: Icons.inbox },
    {
      key: "principal-requests",
      href: "#/principal-requests",
      label: "Principal requests",
      section: "Consent",
      icon: Icons.people,
    },
    { key: "docs", href: "#/docs", label: "Developer docs", section: "Developers", icon: Icons.list },
    { key: "playground", href: "#/playground", label: "API playground", section: "Developers", icon: Icons.code },
    ...(managesApiKeys(role)
      ? [{ key: "api-keys", href: "#/api-keys", label: "API keys", section: "Developers", icon: Icons.shield }]
      : []),
    { key: "webhooks", href: "#/webhooks", label: "Webhooks", section: "Developers", icon: Icons.bell },
    { key: "team", href: "#/team", label: "Team", section: "Organisation", icon: Icons.people },
    { key: "organisation", href: "#/organisation", label: "Organisation", section: "Organisation", icon: Icons.building },
    { key: "billing", href: "#/billing", label: "Billing", section: "Organisation", icon: Icons.card },
    { key: "agreements", href: "#/agreements", label: "Agreements", section: "Organisation", icon: Icons.shield },
    ...(readsAudit(role)
      ? [{ key: "audit", href: "#/audit", label: "Audit", section: "Organisation", icon: Icons.shield }]
      : []),
    { key: "account", href: "#/account", label: "Account", section: "Organisation", icon: Icons.user },
  ];
  // a purpose's pages belong under the purposes in the menu, and a list's under the lists sent
  const current =
    route.page === "purpose" || route.page === "purpose-edit" ? "purposes" : route.page === "batch" ? "batches" : route.page;

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
      {standingNotice(standing?.sts, standing?.leavesAt) ? (
        <div className="cm-error-banner" role="status">
          {standingNotice(standing?.sts, standing?.leavesAt)} {route.page === "agreements" ? null : <a href="#/agreements">Details</a>}
        </div>
      ) : null}
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
      {route.page === "principal-requests" ? <PrincipalRequests console={c} /> : null}
      {route.page === "playground" ? <Playground console={c} /> : null}
      {route.page === "api-keys" ? <ApiKeys console={c} /> : null}
      {route.page === "webhooks" ? <Webhooks console={c} /> : null}
      {route.page === "docs" ? <DevDocs console={c} /> : null}
      {route.page === "send-to-many" ? <SendToMany console={c} /> : null}
      {route.page === "batches" ? <Batches console={c} /> : null}
      {route.page === "batch" ? <BatchDetail key={route.id} console={c} id={route.id} /> : null}
      {route.page === "purposes" ? <Purposes console={c} /> : null}
      {route.page === "purpose" ? <PurposeDetail key={route.prpsCode} console={c} prpsCode={route.prpsCode} /> : null}
      {route.page === "purpose-edit" ? (
        <PurposeEditor key={route.prpsCode ?? "new"} console={c} prpsCode={route.prpsCode} />
      ) : null}
      {route.page === "team" ? (
        <>
          <PageHeader title="Team" subtitle="The people at your organisation who use this console." />
          <Team auth={auth} role={role} gwBase={gwBase} canManage={managesTeam(role)} />
        </>
      ) : null}
      {route.page === "organisation" ? <Organisation console={c} /> : null}
      {route.page === "account" ? <Account console={c} /> : null}
      {route.page === "billing" ? <Billing console={c} /> : null}
      {route.page === "agreements" ? <Agreements console={c} /> : null}
      {route.page === "audit" ? <Audit console={c} /> : null}
    </AppShell>
  );
}
