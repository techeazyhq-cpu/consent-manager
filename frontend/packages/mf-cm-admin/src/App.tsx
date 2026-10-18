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

import { useEffect, useMemo, useState, type ReactNode } from "react";
import type { AuthInfo } from "auth-kit";
import "./admin.css";
import Dashboard from "./Dashboard";
import Onboard from "./Onboard";
import TenantDetail from "./TenantDetail";
import Staff from "./Staff";
import Security from "./Security";
import type { Session } from "./session";
import { parseRoute, type Route } from "./routes";

interface Props {
  auth: AuthInfo;
  gwBase: string;
}

const ICONS: Record<string, ReactNode> = {
  dashboard: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  ),
  onboard: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M4 21V7l8-4 8 4v14" />
      <path d="M9 21v-6h6v6" />
      <path d="M12 7v4M10 9h4" />
    </svg>
  ),
  staff: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20c.8-3.6 3.4-5.5 6.5-5.5s5.7 1.9 6.5 5.5" />
      <path d="M16 4.5a3.5 3.5 0 0 1 0 7M18.5 14.8c1.6.8 2.6 2.5 3 5.2" />
    </svg>
  ),
  security: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M12 3l8 3v6c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V6z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  ),
};

interface NavItem {
  key: Route["page"];
  href: string;
  label: string;
  section: "Tenants" | "Platform";
  show: boolean;
}

/** The current hash route, kept in step with the address bar so Back, Forward and reload all work. */
function useRoute(): Route {
  const [route, setRoute] = useState<Route>(() => parseRoute(window.location.hash));
  useEffect(() => {
    const onChange = () => {
      setRoute(parseRoute(window.location.hash));
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return route;
}

/** CM Admin: a left menu, the dashboard of tenants by default, and a page per tenant. */
export default function CmAdminApp({ auth, gwBase }: Props) {
  const route = useRoute();
  const session = useMemo<Session>(
    () => ({ auth, gwBase, isAdmin: auth.roles.includes("CM_ADMIN") }),
    [auth, gwBase],
  );

  const items = ([
    { key: "dashboard", href: "#/dashboard", label: "Dashboard", section: "Tenants", show: true },
    { key: "onboard", href: "#/onboard", label: "Onboard tenant", section: "Tenants", show: true },
    // only administrators manage staff
    { key: "staff", href: "#/staff", label: "Platform staff", section: "Platform", show: session.isAdmin },
    { key: "security", href: "#/security", label: "Sign-in & security", section: "Platform", show: true },
  ] as NavItem[]).filter((i) => i.show);
  // A tenant's page belongs under the dashboard in the menu.
  const current = route.page === "tenant" ? "dashboard" : route.page;

  return (
    <div className="adm-shell">
      <aside className="adm-side">
        <div className="adm-brand">
          <div className="adm-brand-mark" aria-hidden="true">
            CM
          </div>
          <div>
            CM Admin
            <small>Consent Manager platform</small>
          </div>
        </div>
        <nav className="adm-nav" aria-label="Main">
          {items.map((i, n) => (
            <div key={i.key} style={{ display: "contents" }}>
              {/* a heading above the first item of each section */}
              {n === 0 || items[n - 1].section !== i.section ? <div className="adm-nav-section">{i.section}</div> : null}
              <a href={i.href} aria-current={current === i.key ? "page" : undefined} title={i.label} aria-label={i.label}>
                {ICONS[i.key]}
                <span>{i.label}</span>
              </a>
            </div>
          ))}
        </nav>
        <div className="adm-me">
          <div className="adm-me-name" title={auth.username}>
            {auth.username}
          </div>
          <small>{session.isAdmin ? "Administrator" : "Operator"}</small>
          <div className="adm-me-actions">
            <button className="cm-btn" onClick={auth.logout}>
              Sign out
            </button>
          </div>
        </div>
      </aside>
      <main className="adm-main">
        <div className="cm-page">
          {route.page === "dashboard" ? <Dashboard session={session} /> : null}
          {route.page === "onboard" ? <Onboard session={session} /> : null}
          {route.page === "tenant" ? <TenantDetail key={route.dfId} session={session} dfId={route.dfId} tab={route.tab} /> : null}
          {route.page === "staff" ? (session.isAdmin ? <Staff session={session} /> : <Dashboard session={session} />) : null}
          {route.page === "security" ? <Security session={session} /> : null}
        </div>
      </main>
    </div>
  );
}
