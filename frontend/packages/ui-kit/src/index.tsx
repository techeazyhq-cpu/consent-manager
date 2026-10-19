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

import type { ButtonHTMLAttributes, CSSProperties, PropsWithChildren } from "react";

export function Button({
  variant,
  className,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "danger" }) {
  const cls = ["cm-btn", variant === "primary" ? "cm-btn-primary" : "", variant === "danger" ? "cm-btn-danger" : "", className]
    .filter(Boolean)
    .join(" ");
  return <button className={cls} {...rest} />;
}

export function Card({ children, className, style }: PropsWithChildren<{ className?: string; style?: CSSProperties }>) {
  return (
    <div className={["cm-card", className].filter(Boolean).join(" ")} style={style}>
      {children}
    </div>
  );
}

export function PageHeader({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <div className="cm-page-header">
      <div>
        <h1>{title}</h1>
        {subtitle ? <p>{subtitle}</p> : null}
      </div>
    </div>
  );
}

export function Badge({ status }: { status: string }) {
  return <span className={`cm-badge cm-badge-${status.toLowerCase()}`}>{status.replace(/_/g, " ")}</span>;
}

/** The share of an allowance used, in whole percent (100 when there is none). */
export const shareUsed = (used: number, of: number) => (of > 0 ? Math.min(100, Math.round((used / of) * 100)) : 100);

/** How much of an allowance is used: amber from 80%, red from 100%. */
export function UsageMeter({ used, of, label }: { used: number; of: number; label: string }) {
  const share = shareUsed(used, of);
  const level = share >= 100 ? " cm-meter-full" : share >= 80 ? " cm-meter-high" : "";
  return (
    <div className={`cm-meter${level}`} role="meter" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={share}>
      <div style={{ width: `${share}%` }} />
    </div>
  );
}

export function Spinner({ label = "Loading…" }: { label?: string }) {
  return <div className="cm-spinner">{label}</div>;
}

export function ErrorBanner({ message }: { message: string }) {
  return <div className="cm-error-banner">{message}</div>;
}

/**
 * Shown when the signed-in account is the wrong kind for this app. Keycloak signs a browser in once for every
 * Consent Manager app, so signing in to one app as, say, an administrator signs the next app in as that administrator
 * too; this names the account and lets the person sign out and choose another.
 */
export function WrongAccountNotice({
  appName,
  requiredAccount,
  username,
  onSignOut,
}: {
  appName: string;
  requiredAccount: string;
  username?: string;
  onSignOut: () => void;
}) {
  return (
    <Card className="cm-wrong-account">
      <h1>This account can't use {appName}</h1>
      <p>
        {username ? (
          <>
            You're signed in as <strong>{username}</strong>, which isn't {requiredAccount}.
          </>
        ) : (
          <>The account you're signed in with isn't {requiredAccount}.</>
        )}
      </p>
      <p className="cm-wrong-account-hint">
        This usually means you signed in to another Consent Manager app in this browser.
      </p>
      <Button variant="primary" onClick={onSignOut}>
        Sign out and use another account
      </Button>
    </Card>
  );
}

/** A form control with its label. The label wraps the control, so it names it for assistive tech and focuses it. */
export function Field({ label, children }: PropsWithChildren<{ label: string }>) {
  return (
    <label className="cm-field">
      <span className="cm-field-label">{label}</span>
      {children}
    </label>
  );
}

/** A problem's human sentence ({@code detail}) before its short type name ({@code title}). */
export function friendlyError(err: unknown): string {
  if (err && typeof err === "object" && "problem" in err) {
    const p = (err as { problem?: { title?: string; detail?: string } }).problem;
    if (p?.detail) return p.detail;
    if (p?.title) return p.title;
  }
  if (err instanceof Error) return err.message;
  return "Something went wrong";
}

export { AuditTable, describeAudit, type AuditRow } from "./audit";
export { AppShell, Icons, OrgLogo, forgetLogo, humanise, useHashRoute, type NavItem } from "./shell";
