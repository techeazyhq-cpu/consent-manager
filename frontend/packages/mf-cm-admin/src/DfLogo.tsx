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
import { dfReg } from "api-client";
import { freshToken, type Session } from "./session";

/** Logos already fetched this visit, so the dashboard and a tenant's page don't download them twice. */
const cache = new Map<string, string | null>();

export function forgetLogo(dfId: string) {
  const url = cache.get(dfId);
  if (url) URL.revokeObjectURL(url);
  cache.delete(dfId);
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
 * A fiduciary's logo. It needs the signed-in staff member's token, so it's fetched rather than linked; until it
 * arrives, or if there is none, the fiduciary's initials stand in.
 */
export default function DfLogo({
  session,
  dfId,
  name,
  size = 72,
  version = 0,
}: {
  session: Session;
  dfId: string;
  name: string;
  size?: number;
  /** Bumped after an upload, to fetch the new logo. */
  version?: number;
}) {
  const [url, setUrl] = useState<string | null | undefined>(cache.get(dfId));

  useEffect(() => {
    let live = true;
    if (cache.has(dfId)) {
      setUrl(cache.get(dfId));
      return;
    }
    freshToken(session.auth)
      .then((token) => dfReg.logo(session.gwBase, token, dfId))
      .then((blob) => {
        const objectUrl = blob ? URL.createObjectURL(blob) : null;
        cache.set(dfId, objectUrl);
        if (live) setUrl(objectUrl);
      })
      .catch(() => live && setUrl(null));
    return () => {
      live = false;
    };
  }, [session, dfId, version]);

  const box = { width: size, height: size, fontSize: Math.round(size / 2.8) };
  if (url) {
    return (
      <div className="adm-logo" style={box}>
        <img src={url} alt={`${name} logo`} />
      </div>
    );
  }
  return (
    <div className="adm-logo" style={{ ...box, background: colourOf(dfId) }} aria-label={`${name} (no logo)`} role="img">
      {initials(name)}
    </div>
  );
}
