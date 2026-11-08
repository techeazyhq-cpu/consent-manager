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

import type { DfRole, DfSts } from "api-client";

/** The consent manager's documents, in the words people read them in. */
export const DOCUMENT_LABELS: Record<string, string> = {
  terms: "Terms of service",
  dpa: "Data processing agreement",
};

/** Only an Owner accepts the documents or asks to leave: it binds the organisation (ADR-063, ADR-064). */
export const bindsOrganisation = (role: DfRole) => role === "OWNER";

/** What a standing means for the people using the console, or null when nothing needs saying. */
export function standingNotice(sts: DfSts | undefined, leavesAt?: string | null): string | null {
  const day = leavesAt ? new Date(leavesAt).toLocaleDateString(undefined, { day: "numeric", month: "long", year: "numeric" }) : "";
  switch (sts) {
    case "LEAVING":
      return `Your organisation is leaving Consent Manager. No new requests are accepted and API keys have stopped working; your records stay readable until ${day || "the wind-down ends"}, when the organisation closes.`;
    case "SUSPENDED":
      return "Your organisation is suspended. No new consent requests are accepted and API keys have stopped working. Contact the platform.";
    case "CLOSED":
      return "Your organisation has left Consent Manager.";
    default:
      return null;
  }
}

/** The words that must be typed to leave: the organisation's name, any case, no stray spaces. */
export const confirmsName = (typed: string, name: string) => typed.trim().toLowerCase() === name.trim().toLowerCase() && typed.trim() !== "";
