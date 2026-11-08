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

/** The public sign-up page's routes, in the URL hash (ADR-065): the form, and the page a confirmation link opens. */
export type SignupRoute = { page: "form" } | { page: "verify"; id: string; token: string };

/** The route when the hash is the sign-up page's, or null for the console proper (whose own sign-in uses the hash too). */
export function parseSignupRoute(hash: string): SignupRoute | null {
  const [path, query = ""] = hash.replace(/^#\/?/, "").split("?");
  const parts = path.split("/").filter(Boolean);
  if (parts[0] !== "signup") return null;
  if (parts[1] === "verify") {
    const params = new URLSearchParams(query);
    return { page: "verify", id: params.get("id") ?? "", token: params.get("token") ?? "" };
  }
  return { page: "form" };
}

/** What the form has, trimmed; the optional fields are left out when empty. */
export function applicationFrom(form: { name: string; contactEmail: string; legalName: string; industry: string; website: string }) {
  const out: { name: string; contactEmail: string; legalName?: string; industry?: string; website?: string } = {
    name: form.name.trim(),
    contactEmail: form.contactEmail.trim(),
  };
  if (form.legalName.trim()) out.legalName = form.legalName.trim();
  if (form.industry.trim()) out.industry = form.industry.trim();
  if (form.website.trim()) out.website = form.website.trim();
  return out;
}

/** Enough to try sending: a name, and something that looks like an address (the platform checks properly). */
export const canApply = (name: string, email: string) => name.trim().length > 0 && /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim());
