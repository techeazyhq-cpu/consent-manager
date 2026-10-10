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
}

export interface DfNameRespDto {
  id: string;
  name: string;
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
