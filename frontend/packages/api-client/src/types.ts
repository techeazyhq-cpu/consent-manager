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

// Wire shapes mirrored 1:1 from the backend if-rest DTOs (plan §13's naming rules apply
// here too — abbreviated field names match the Java records exactly).

export type ExtDpRefTyp = "EMAIL" | "PHONE";
export type CnsReqSts = "PENDING" | "GRANTED" | "DENIED" | "EXPIRED" | "NOTIFICATION_FAILED";
export type CnsArtSts = "ISSUED" | "REVOKED" | "EXPIRED";
export type CnsLcySts = "PENDING" | "DENIED" | "ISSUED" | "REVOKED" | "NOTIFICATION_FAILED" | "EXPIRED";
export type DfSts = "PENDING" | "ACTIVE";
export type DfSubSts = "ACTIVE" | "SUSPENDED";

export interface ProblemDetail {
  type: string;
  title: string;
  status?: number;
  /** The human-readable explanation, when the service gives one. */
  detail?: string;
}

export interface DfRespDto {
  id: string;
  name: string;
  sts: DfSts;
  cmDfApiClientSecret: string | null;
}

export interface DfListItemRespDto {
  id: string;
  name: string;
  contactEmail: string;
  sts: DfSts;
  registeredAt: string;
  legalName?: string | null;
  industry?: string | null;
  /** The plan chosen at onboarding; null means FREE. */
  subPlnName?: string | null;
}

/** What onboarding asks for; everything after the plan is the organisation's profile, all optional. */
export interface DfRegisterReqDto {
  name: string;
  contactEmail: string;
  subPlnName?: string;
  legalName?: string;
  contactPhone?: string;
  website?: string;
  registeredAddress?: string;
  industry?: string;
  cin?: string;
  gstin?: string;
  dpoName?: string;
  dpoEmail?: string;
}

export interface DfProfileRespDto {
  id: string;
  name: string;
  contactEmail: string;
  sts: DfSts;
  subPlnName: string | null;
  registeredAt: string;
  activatedAt: string | null;
  legalName: string | null;
  contactPhone: string | null;
  website: string | null;
  registeredAddress: string | null;
  industry: string | null;
  cin: string | null;
  gstin: string | null;
  dpoName: string | null;
  dpoEmail: string | null;
}

/** A tenant's consent requests by lifecycle status, for CM Admin (counts only). */
export interface CnsStats {
  dfId: string;
  sent: number;
  pending: number;
  granted: number;
  denied: number;
  revoked: number;
  expired: number;
  notificationFailed: number;
}

/** A tenant's consent request as staff see it: the data principal only as a pseudonym. */
export interface AdminCnsReqView {
  cnsReqId: string;
  cnsArtId: string | null;
  dpPseudonym: string;
  prpsCode: string;
  dCatCodes: string[];
  lcySts: CnsLcySts;
  validUntil: string | null;
  createdAt: string;
  updatedAt: string;
}

export type StfRole = "CM_ADMIN" | "CM_OPERATOR";

export interface StfAcct {
  id: string;
  email: string;
  name: string;
  role: StfRole;
  enabled: boolean;
  createdAt: string | null;
}

export interface StfSession {
  id: string;
  ipAddress: string;
  startedAt: string | null;
  lastAccessAt: string | null;
  clients: string[];
}

export interface DfNameRespDto {
  id: string;
  name: string;
}

/** What a person at a fiduciary may do (ADR-033). */
export type DfRole = "OWNER" | "ADMIN" | "CONSENT_MANAGER" | "DEVELOPER" | "AUDITOR";

export interface DfMemberRespDto {
  id: string;
  email: string;
  name: string;
  role: DfRole;
}

export interface CnsReqRespDto {
  id: string;
  dfId: string;
  sts: CnsReqSts;
  prpsCode: string;
}

export interface CnsArtRespDto {
  id: string;
  cnsReqId: string;
  dfId: string;
  sts: CnsArtSts;
  cnsTkn: string;
}

export interface CnsArtRdMdl {
  cnsReqId: string;
  cnsArtId: string | null;
  dfId: string;
  dpId: string | null;
  prpsCode: string;
  dCatCodes: string[];
  lcySts: CnsLcySts;
  validFrom: string | null;
  validUntil: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface DpDashboardRow {
  dfId: string;
  activeCnt: number;
  pendingCnt: number;
  lastUpdatedAt: string;
}

export interface SubPln {
  id: string;
  name: string;
  reqPerSecQta: number;
  reqPerMonthQta: number;
}

export interface DfSubStatusResult {
  subPlnId: string;
  subPlnName: string;
  dfSubSts: DfSubSts;
  curPeriodUsg: number;
  reqPerMonthQta: number;
}

export interface UsageRespDto {
  currentPeriodUsage: number;
}

export type PushPltf = "WEB";

/** One of the calling DP's push devices. The token itself is never returned. */
export interface PushDvcRespDto {
  id: string;
  platform: PushPltf;
  registeredAt: string;
  lastSeenAt: string;
}

/** Shape passed from shell-app into the DP Portal / CM Admin remotes (props, not context —
 * avoids the classic Module-Federation problem of a custom React Context object being a
 * different instance in the host's bundle vs. a remote's own bundle). */
export interface AuthInfo {
  token: string;
  subject: string;
  roles: string[];
  dpId?: string;
  email?: string;
  logout: () => void;
}
