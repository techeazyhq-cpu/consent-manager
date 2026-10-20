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
  prpsVer: number | null;
  ntcLang: string | null;
  /** SHA-256 of the notice the person is shown. */
  ntcHash: string | null;
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
  /** The notice the fiduciary wrote with its request; absent on requests raised before notices were recorded here. */
  ntcTitle?: string | null;
  ntcText?: string | null;
  /** What the person granted (ADR-026): some or all of dCatCodes; null until consent is given. */
  grantedDCatCodes?: string[] | null;
}

/** What a person chooses when giving consent; either may be left out (everything asked, the requested validity). */
export interface GrantChoice {
  dCatCodes?: string[];
  validityDays?: number;
}

/** What a request asks, for the decision screen (ADR-026, ADR-028). Needed categories come first. */
export interface CnsReqTermsDto {
  cnsReqId: string;
  prpsCode: string;
  prpsVer: number | null;
  name: string | null;
  description: string | null;
  dCats: { code: string; dpv: string | null; sensitivity: PrpsSensitivity; necessary: boolean; consequence: string | null }[];
  requestedValidityDays: number;
  maxValidityDays: number;
  retentionDays: number | null;
  recipients: string[];
  transfers: string[];
  flags: string[];
  ntcLang: string | null;
  ntcTitle: string;
  ntcText: string | null;
  ntcHash: string | null;
}

/** One event of a consent record, as its ISO/IEC TS 27560 document lists it (ADR-025). */
export interface RecordEvent {
  seq: number;
  time: string;
  type: "requested" | "given" | "refused" | "modified" | "renewed" | "withdrawn" | "lapsed";
  /** W3C DPV consent status, such as `dpv:ConsentGiven`. */
  status: string;
  by: "fiduciary" | "principal" | "platform";
  personalData?: string[];
  validFrom?: string;
  validUntil?: string;
  duration?: string;
  change?: string;
}

/** A consent record as its 27560 JSON-LD document; only the parts the portal shows are typed. */
export interface ConsentRecordDoc {
  "@id": string;
  header: { recordId: string; consentRequest: string; createdAt: string };
  notice: { language?: string; hash?: string; version?: number; title?: string; text?: string };
  processing: {
    purposes: { code: string; name?: string; description?: string }[];
    personalData: { code: string; "@type": string; sensitivity: string; necessary: boolean; consequenceOfRefusal?: string | null }[];
    controller: { name: string };
    recipients: string[];
    transfers: string[];
    storageDuration?: string | null;
    jurisdictions: string[];
    withdrawal: { method: string; url: string };
    rights: string[];
  };
  parties: { consentManager: { name: string; grievanceContact: string } };
  events: RecordEvent[];
  status: string | null;
}

/** A signed receipt for one record event: a compact JWS, verifiable with the published receipt keys. */
export interface ReceiptView {
  receiptId: string;
  forEvent: number;
  issuedAt: string;
  kid: string;
  sha256: string;
  jws: string;
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
  reqPerSecQta: number;
  /** The calendar month (UTC) usage is counted in, e.g. "2026-10". */
  usagePeriod: string;
  /** When the count starts again. */
  resetsAt: string;
}

/** One tenant on CM Admin's billing overview (ADR-039). */
export interface BilOverviewRow {
  dfId: string;
  subPlnName: string;
  dfSubSts: DfSubSts;
  curPeriodUsg: number;
  reqPerMonthQta: number;
  reqPerSecQta: number;
}

/** An API key as listed: never the key itself, which is shown once when issued (ADR-041). */
export interface ApiKeyDto {
  id: string;
  name: string;
  keyPrefix: string;
  signingRequired: boolean;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

export interface IssuedApiKeyDto {
  key: ApiKeyDto;
  apiKey: string;
  signingSecret: string;
}

/** Outcomes of requests: given, refused, still awaiting, or never reached the person (ADR-049). */
export interface InsightOutcomes {
  raised: number;
  given: number;
  refused: number;
  awaiting: number;
  notNotified: number;
}

/** How a fiduciary's requests went over a window (ADR-049). Durations in seconds. */
export interface DfInsightsDto {
  from: string;
  to: string;
  outcomes: InsightOutcomes;
  decisions: { decided: number; medianSeconds: number | null; p90Seconds: number | null; withinADay: number };
  purposes: { prpsCode: string; outcomes: InsightOutcomes; medianSeconds: number | null }[];
  /** Every day of the window, UTC: raised that day, and given or refused that day. */
  days: { date: string; raised: number; given: number; refused: number }[];
  changes: { withdrawn: number; narrowed: number; renewed: number; lapsed: number };
}

/** A fiduciary's webhook endpoint (ADR-047); its secret is never shown again after it's made or rotated. */
export interface WebhookEndpointDto {
  id: string;
  url: string;
  description: string | null;
  /** The message types it receives; all of them when empty. */
  types: string[];
  active: boolean;
  createdAt: string;
  /** While a rotated-out secret is still honoured, when it stops being. */
  previousSecretUntil: string | null;
}

export interface WebhookWithSecretDto {
  endpoint: WebhookEndpointDto;
  /** The signing secret ({@code whsec_…}): shown this once. */
  secret: string;
}

export type WebhookDeliverySts = "PENDING" | "DELIVERED" | "FAILED";

export interface WebhookDeliveryDto {
  id: string;
  endpointId: string;
  msgId: string;
  type: string;
  sts: WebhookDeliverySts;
  attempts: number;
  nextAttemptAt: string | null;
  lastStatus: number | null;
  lastError: string | null;
  deliveredAt: string | null;
  createdAt: string;
  payload: string | null;
}

export interface WebhookAttemptDto {
  n: number;
  at: string;
  status: number | null;
  error: string | null;
  millis: number;
}

export interface WebhookDeliveryDetailDto {
  delivery: WebhookDeliveryDto;
  attempts: WebhookAttemptDto[];
}

/** One audit ledger entry as people read it (ADR-040). */
export interface AudActivityRow {
  seq: number;
  evtType: string;
  occurredAt: string;
  dfId: string | null;
  cnsReqId: string | null;
  entryHash: string;
  actor: { kind: string; name: string | null };
  target?: string | null;
  details: Record<string, string>;
  erased: boolean;
}

/** The ledger's own check of itself: every hash recomputed, every checkpoint's timestamp checked. */
export interface AudVerifyResult {
  chain: {
    intact: boolean;
    entriesChecked: number;
    payloadsErased: number;
    firstBrokenSeq: number | null;
    reason: string | null;
    headSeq: number;
    headHash: string;
  };
  checkpoints: { intact: boolean; checkpoints: number; anchored: number; firstBrokenId: number | null; reason: string | null };
}

export type PlnChgReqSts = "PENDING" | "APPROVED" | "DECLINED" | "WITHDRAWN";

/** A fiduciary asking for another plan, and the platform's decision (ADR-039). */
export interface PlnChgReqDto {
  id: string;
  dfId: string;
  fromPlnName: string;
  toPlnName: string;
  note: string | null;
  sts: PlnChgReqSts;
  requestedByName: string | null;
  requestedAt: string;
  decidedByName: string | null;
  decidedAt: string | null;
  decisionNote: string | null;
}

export interface UsageRespDto {
  currentPeriodUsage: number;
}

/** A browser running the DP portal, or the DP app on a phone. Every one registers an FCM token. */
export type PushPltf = "WEB" | "ANDROID" | "IOS";

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

/**
 * What a request asks (ADR-031): a published purpose version, the notice's language (English or the first written
 * when absent), and the data categories (all the version's when absent or empty). A batch asks every recipient this.
 */
export interface CnsReqBatchTemplate {
  prpsCode: string;
  prpsVer: number;
  ntcLang?: string;
  dCatCodes?: string[];
}

export type PrpsSensitivity = "ORDINARY" | "SENSITIVE" | "PHI" | "CHILD";
export type PrpsFlag = "TRACKING" | "BEHAVIOURAL_MONITORING" | "TARGETED_ADVERTISING";

/** A data category as a purpose version defines it (ADR-026, ADR-031). */
export interface PrpsDCat {
  code: string;
  /** W3C DPV personal data type, such as `dpv-pd:EmailAddress`. */
  dpv?: string | null;
  sensitivity: PrpsSensitivity;
  /** Needed for the purpose; the person may still refuse or withdraw it. */
  necessary: boolean;
  /** What happens without it; required when necessary. */
  consequence?: string | null;
}

/** A notice in one language; `hash` is filled in by the platform. */
export interface PrpsNtc {
  lang: string;
  title: string;
  text: string;
  hash?: string;
}

/** What one version of a purpose says. */
export interface PrpsContent {
  name: string;
  description?: string | null;
  dCats: PrpsDCat[];
  recipients: string[];
  transfers: string[];
  retentionDays: number;
  requestedValidityDays: number;
  maxValidityDays: number;
  flags: PrpsFlag[];
  notices: PrpsNtc[];
}

/** How a version differs from the one before, as the platform classifies it. */
export interface PrpsChange {
  kind: "INITIAL" | "MATERIAL" | "NON_MATERIAL";
  /** Reasons it needs re-consent. */
  material: string[];
  minor: string[];
}

export interface PrpsVer {
  id: string;
  verNo: number;
  sts: "DRAFT" | "PUBLISHED";
  content: PrpsContent;
  change: PrpsChange | null;
  createdAt: string;
  publishedAt: string | null;
}

/** A purpose with every version, oldest first; requests are made from `currentVer`. */
export interface PrpsDefDto {
  id: string;
  code: string;
  currentVer: number | null;
  draftVer: number | null;
  createdAt: string;
  versions: PrpsVer[];
}

/** A purpose in a list, with its current version's content and any draft's. */
export interface PrpsSummaryDto {
  id: string;
  code: string;
  currentVer: number | null;
  draftVer: number | null;
  current: PrpsContent | null;
  draft: PrpsContent | null;
  updatedAt: string;
}

/** A recipient: `contact` (email or mobile), or `email` or `phone`; `ref` is the fiduciary's own reference. */
export interface CnsReqBatchRecipient {
  contact?: string;
  email?: string;
  phone?: string;
  ref?: string;
}

export interface CnsReqBatchReqDto extends CnsReqBatchTemplate {
  recipients: CnsReqBatchRecipient[];
}

export type CnsReqBatchOutcome = "PENDING" | "SENT" | "INVALID" | "DUPLICATE" | "FAILED";

export interface CnsReqBatchDto {
  id: string;
  sts: "QUEUED" | "SENDING" | "DONE";
  source: "API" | "CSV" | "XLSX";
  fileName: string | null;
  prpsCode: string;
  prpsVer: number | null;
  ntcLang: string | null;
  dCatCodes: string[];
  ntcTitle: string;
  total: number;
  valid: number;
  invalid: number;
  duplicate: number;
  sent: number;
  failed: number;
  pending: number;
  createdAt: string;
  finishedAt: string | null;
}

export interface CnsReqBatchRowDto {
  rowNo: number;
  contact: string;
  ref: string | null;
  outcome: CnsReqBatchOutcome;
  cnsReqId: string | null;
  problem: string | null;
}

export interface CnsReqBatchSubmitRespDto {
  /** False for a dry run. */
  stored: boolean;
  batch: CnsReqBatchDto;
  /** The first problem rows; the batch's rows hold all of them. */
  problems: CnsReqBatchRowDto[];
  /** Requests left this month (after this batch, if stored). */
  available: number;
}
