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

import { useEffect, useState, type ReactNode } from "react";

export interface NavItem {
  key: string;
  href: string;
  label: string;
  /** Items are grouped under these headings, in the order they first appear. */
  section: string;
  icon: ReactNode;
  /** A count shown beside the label, such as requests awaiting a decision. */
  badge?: number;
}

/**
 * The frame shared by the apps with a left menu (CM Admin, the DP Portal): brand, grouped menu, who is signed in and
 * a way out. On narrow screens the menu becomes a bar of icons across the top.
 */
export function AppShell({
  appName,
  tagline,
  mark,
  items,
  current,
  userName,
  userRole,
  footer,
  onSignOut,
  children,
}: {
  appName: string;
  tagline: string;
  mark: string;
  items: NavItem[];
  current: string;
  userName?: string;
  userRole: string;
  /** Extra controls above Sign out, such as a notifications switch. */
  footer?: ReactNode;
  onSignOut: () => void;
  children: ReactNode;
}) {
  return (
    <div className="cm-shell">
      <aside className="cm-side">
        <div className="cm-brand">
          <div className="cm-brand-mark" aria-hidden="true">
            {mark}
          </div>
          <div>
            {appName}
            <small>{tagline}</small>
          </div>
        </div>
        <nav className="cm-sidenav" aria-label="Main">
          {items.map((i, n) => (
            <div key={i.key} style={{ display: "contents" }}>
              {n === 0 || items[n - 1].section !== i.section ? <div className="cm-sidenav-section">{i.section}</div> : null}
              <a
                href={i.href}
                aria-current={current === i.key ? "page" : undefined}
                title={i.label}
                aria-label={i.badge ? `${i.label} (${i.badge})` : i.label}
              >
                {i.icon}
                <span>{i.label}</span>
                {i.badge ? <b className="cm-sidenav-badge">{i.badge}</b> : null}
              </a>
            </div>
          ))}
        </nav>
        <div className="cm-me">
          <div className="cm-me-name" title={userName}>
            {userName}
          </div>
          <small>{userRole}</small>
          <div className="cm-me-actions">
            {footer}
            <button className="cm-btn" onClick={onSignOut}>
              Sign out
            </button>
          </div>
        </div>
      </aside>
      <main className="cm-main">
        <div className="cm-page">{children}</div>
      </main>
    </div>
  );
}

/** The page addressed by the URL hash, kept in step with the address bar so Back, Forward and reload all work. */
export function useHashRoute<R>(parse: (hash: string) => R): R {
  const [route, setRoute] = useState<R>(() => parse(window.location.hash));
  useEffect(() => {
    const onChange = () => {
      setRoute(parse(window.location.hash));
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  return route;
}

/** Logos already fetched this visit, so a dashboard and a detail page don't download them twice. */
const logoCache = new Map<string, string | null>();

export function forgetLogo(id: string) {
  const url = logoCache.get(id);
  if (url) URL.revokeObjectURL(url);
  logoCache.delete(id);
}

const PALETTE = ["#4f8cff", "#35c98f", "#f0a93a", "#ef5a6f", "#9b7bff", "#2fb5c8", "#e07b39", "#7c9a3a"];

function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  return (words.length > 1 ? words[0][0] + words[1][0] : name.slice(0, 2)).toUpperCase();
}

function colourOf(id: string): string {
  let h = 0;
  for (const c of id) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return PALETTE[h % PALETTE.length];
}

/**
 * An organisation's logo, fetched by {@code load} (it needs the signed-in person's token, so it can't simply be
 * linked); until it arrives, or if there is none, the organisation's initials on a colour taken from its id.
 */
export function OrgLogo({
  id,
  name,
  load,
  size = 72,
  version = 0,
}: {
  id: string;
  name: string;
  load: () => Promise<Blob | null>;
  size?: number;
  /** Bumped after an upload, to fetch the new logo. */
  version?: number;
}) {
  const [url, setUrl] = useState<string | null | undefined>(logoCache.get(id));

  useEffect(() => {
    let live = true;
    if (logoCache.has(id)) {
      setUrl(logoCache.get(id));
      return;
    }
    load()
      .then((blob) => {
        const objectUrl = blob ? URL.createObjectURL(blob) : null;
        logoCache.set(id, objectUrl);
        if (live) setUrl(objectUrl);
      })
      .catch(() => live && setUrl(null));
    return () => {
      live = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, version]);

  const box = { width: size, height: size, fontSize: Math.round(size / 2.8) };
  if (url) {
    return (
      <div className="cm-logo" style={box}>
        <img src={url} alt={`${name} logo`} />
      </div>
    );
  }
  return (
    <div className="cm-logo" style={{ ...box, background: colourOf(id) }} aria-label={`${name} (no logo)`} role="img">
      {initials(name)}
    </div>
  );
}

/** A code such as {@code ORDER_FULFILLMENT} as words a person reads: "Order fulfillment". */
export function humanise(code: string): string {
  const words = code.replace(/[_-]+/g, " ").trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Small line icons for menus, drawn with the current text colour. */
export const Icons: Record<string, ReactNode> = {
  dashboard: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
  ),
  building: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M4 21V7l8-4 8 4v14" />
      <path d="M9 21v-6h6v6" />
      <path d="M12 7v4M10 9h4" />
    </svg>
  ),
  people: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20c.8-3.6 3.4-5.5 6.5-5.5s5.7 1.9 6.5 5.5" />
      <path d="M16 4.5a3.5 3.5 0 0 1 0 7M18.5 14.8c1.6.8 2.6 2.5 3 5.2" />
    </svg>
  ),
  shield: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M12 3l8 3v6c0 4.5-3.4 8.2-8 9-4.6-.8-8-4.5-8-9V6z" />
      <path d="M9 12l2 2 4-4" />
    </svg>
  ),
  inbox: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M3 13l2.5-8h13L21 13v6H3z" />
      <path d="M3 13h5l1.5 2.5h5L16 13h5" />
    </svg>
  ),
  list: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M9 6h11M9 12h11M9 18h11" />
      <circle cx="4.5" cy="6" r="1.2" />
      <circle cx="4.5" cy="12" r="1.2" />
      <circle cx="4.5" cy="18" r="1.2" />
    </svg>
  ),
  bell: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15z" />
      <path d="M10 20a2 2 0 0 0 4 0" />
    </svg>
  ),
  send: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M21 3L3 10.5l7 2.5 2.5 7z" />
      <path d="M10 13l11-10" />
    </svg>
  ),
  upload: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <path d="M12 15V3M7 8l5-5 5 5" />
      <path d="M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4" />
    </svg>
  ),
  user: (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 21c1-4 4.2-6 8-6s7 2 8 6" />
    </svg>
  ),
};
