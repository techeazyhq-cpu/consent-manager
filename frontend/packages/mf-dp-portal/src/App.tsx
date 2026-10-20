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
import { AppShell, ErrorBanner, Icons, useHashRoute, type NavItem } from "ui-kit/src/index";
import "./portal.css";
import { forgetThisBrowser } from "./PushToggle";
import { onForegroundPush } from "./push";
import { usePortal } from "./portal";
import { Account, Consents, Dashboard, Notifications, OrgDetail, Requests } from "./pages";
import { parseRoute } from "./routes";
import { History } from "./History";

interface Props {
  auth: AuthInfo;
  gwBase: string;
}

/** The DP Portal: a left menu, a dashboard of the organisations the person deals with, and a page for each. */
export default function DpPortalApp({ auth, gwBase }: Props) {
  const route = useHashRoute(parseRoute);
  const portal = usePortal(auth, gwBase);
  const [notice, setNotice] = useState<string | null>(null);

  // A push that arrives while the portal is open is shown here rather than as a system notification.
  useEffect(() => {
    let unsubscribe = () => {};
    onForegroundPush((payload) => {
      setNotice(payload.notification?.body ?? payload.data?.body ?? "An organisation asked for your consent");
      portal.reload();
    }).then((fn) => (unsubscribe = fn));
    return () => unsubscribe();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Signing out also stops this browser getting the person's notifications (it may be a shared computer).
  const signOut = async () => {
    await forgetThisBrowser(auth, gwBase).catch(() => {});
    auth.logout();
  };

  const items: NavItem[] = [
    { key: "dashboard", href: "#/dashboard", label: "Dashboard", section: "Consents", icon: Icons.dashboard },
    {
      key: "requests",
      href: "#/requests",
      label: "Requests",
      section: "Consents",
      icon: Icons.inbox,
      badge: portal.awaiting.length || undefined,
    },
    { key: "consents", href: "#/consents", label: "My consents", section: "Consents", icon: Icons.list },
    { key: "notifications", href: "#/notifications", label: "Notifications", section: "You", icon: Icons.bell },
    { key: "account", href: "#/account", label: "Account", section: "You", icon: Icons.user },
  ];
  // An organisation's page belongs under the dashboard in the menu.
  const current = route.page === "org" ? "dashboard" : route.page === "history" ? "consents" : route.page;

  return (
    <AppShell
      appName="My consents"
      tagline="Consent Manager"
      mark="CM"
      items={items}
      current={current}
      userName={auth.email ?? auth.username}
      userRole="Data principal"
      onSignOut={signOut}
    >
      {portal.error ? <ErrorBanner message={portal.error} /> : null}
      {notice ? (
        <div className="cm-notice-banner" role="status">
          {notice}{" "}
          <button className="cm-btn" onClick={() => setNotice(null)}>
            Dismiss
          </button>
        </div>
      ) : null}
      {route.page === "dashboard" ? <Dashboard portal={portal} /> : null}
      {route.page === "requests" ? <Requests portal={portal} /> : null}
      {route.page === "consents" ? <Consents portal={portal} /> : null}
      {route.page === "history" ? <History key={route.cnsReqId} portal={portal} cnsReqId={route.cnsReqId} /> : null}
      {route.page === "org" ? <OrgDetail portal={portal} dfId={route.dfId} /> : null}
      {route.page === "notifications" ? <Notifications portal={portal} /> : null}
      {route.page === "account" ? <Account portal={portal} onSignOut={signOut} /> : null}
    </AppShell>
  );
}
