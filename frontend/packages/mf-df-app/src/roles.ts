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

import type { DfRole } from "api-client";

/** Most powerful first, as the backend reads them (ADR-033). */
const ROLES: DfRole[] = ["OWNER", "ADMIN", "CONSENT_MANAGER", "DEVELOPER", "AUDITOR"];

export const ROLE_LABELS: Record<DfRole, string> = {
  OWNER: "Owner",
  ADMIN: "Administrator",
  CONSENT_MANAGER: "Consent manager",
  DEVELOPER: "Developer",
  AUDITOR: "Auditor",
};

export const ROLE_HINTS: Record<DfRole, string> = {
  OWNER: "Everything, including who else is an Owner",
  ADMIN: "The team, API clients and the organisation",
  CONSENT_MANAGER: "Purposes, notices and consent requests",
  DEVELOPER: "API clients and webhooks in the sandbox",
  AUDITOR: "Read-only",
};

/** The member role among a token's realm roles ({@code DF_OWNER} and so on), if any. */
export function memberRoleOf(realmRoles: string[]): DfRole | undefined {
  return ROLES.find((role) => realmRoles.includes(`DF_${role}`));
}

export const managesTeam = (role: DfRole) => role === "OWNER" || role === "ADMIN";

/** Who takes the organisation's sandbox keys (ADR-038): the people who build integrations. */
export const usesSandbox = (role: DfRole) => role === "OWNER" || role === "ADMIN" || role === "DEVELOPER";

/** Who reads the organisation's audit (ADR-040): Owners, Administrators and Auditors. */
export const readsAudit = (role: DfRole) => role === "OWNER" || role === "ADMIN" || role === "AUDITOR";

/** Who manages the organisation's API keys (ADR-041): Owners, Administrators and Developers. */
export const managesApiKeys = (role: DfRole) => role === "OWNER" || role === "ADMIN" || role === "DEVELOPER";

export const raisesRequests = (role: DfRole) => role === "OWNER" || role === "ADMIN" || role === "CONSENT_MANAGER";

/** The roles {@code role} may give: only an Owner gives ownership. */
export const assignableBy = (role: DfRole): DfRole[] => (role === "OWNER" ? ROLES : ROLES.filter((r) => r !== "OWNER"));
