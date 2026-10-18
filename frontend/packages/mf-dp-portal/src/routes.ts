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

/** The portal's pages, addressed by the URL hash so each can be bookmarked, reloaded and reached with Back. */
export type Route =
  | { page: "dashboard" }
  | { page: "requests" }
  | { page: "consents" }
  | { page: "notifications" }
  | { page: "account" }
  | { page: "org"; dfId: string };

/** Anything unrecognised lands on the dashboard, the default page. */
export function parseRoute(hash: string): Route {
  const parts = hash.replace(/^#\/?/, "").split("/").filter(Boolean).map(decodeURIComponent);
  switch (parts[0]) {
    case "requests":
    case "consents":
    case "notifications":
    case "account":
      return { page: parts[0] };
    case "organisations":
      return parts[1] ? { page: "org", dfId: parts[1] } : { page: "dashboard" };
    default:
      return { page: "dashboard" };
  }
}

export const orgHref = (dfId: string) => `#/organisations/${encodeURIComponent(dfId)}`;
