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

/** The DF Console's pages, addressed by the URL hash so each can be bookmarked, reloaded and reached with Back. */
export type Route =
  | { page: "dashboard" }
  | { page: "raise"; prpsCode?: string }
  | { page: "requests" }
  | { page: "team" }
  | { page: "organisation" }
  | { page: "account" }
  | { page: "purpose"; prpsCode: string }
  | { page: "send-to-many" }
  | { page: "batches" }
  | { page: "batch"; id: string }
  | { page: "playground" };

/** Anything unrecognised lands on the dashboard, the default page. */
export function parseRoute(hash: string): Route {
  const parts = hash.replace(/^#\/?/, "").split("/").filter(Boolean).map(decodeURIComponent);
  switch (parts[0]) {
    case "raise":
      return parts[1] ? { page: "raise", prpsCode: parts[1] } : { page: "raise" };
    case "requests":
    case "team":
    case "organisation":
    case "account":
      return { page: parts[0] };
    case "playground":
      return { page: "playground" };
    case "send-to-many":
      return { page: "send-to-many" };
    case "batches":
      return parts[1] ? { page: "batch", id: parts[1] } : { page: "batches" };
    case "purposes":
      return parts[1] ? { page: "purpose", prpsCode: parts[1] } : { page: "dashboard" };
    default:
      return { page: "dashboard" };
  }
}

export const purposeHref = (prpsCode: string) => `#/purposes/${encodeURIComponent(prpsCode)}`;

/** The request form, with the purpose filled in when raised from a purpose's page. */
export const raiseHref = (prpsCode?: string) => (prpsCode ? `#/raise/${encodeURIComponent(prpsCode)}` : "#/raise");

/** A list sent to many people, with its progress and each row's outcome. */
export const batchHref = (id: string) => `#/batches/${encodeURIComponent(id)}`;
