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

export const raisesRequests = (role: DfRole) => role === "OWNER" || role === "ADMIN" || role === "CONSENT_MANAGER";

/** The roles {@code role} may give: only an Owner gives ownership. */
export const assignableBy = (role: DfRole): DfRole[] => (role === "OWNER" ? ROLES : ROLES.filter((r) => r !== "OWNER"));
