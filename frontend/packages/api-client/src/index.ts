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

import type {
  CnsArtRdMdl,
  CnsArtRespDto,
  CnsReqRespDto,
  CnsReqTermsDto,
  ConsentRecordDoc,
  ReceiptView,
  GrantChoice,
  DfListItemRespDto,
  DfMemberRespDto,
  DfProfileRespDto,
  DfRegisterReqDto,
  AdminCnsReqView,
  CnsLcySts,
  CnsStats,
  StfAcct,
  StfSession,
  CnsReqBatchReqDto,
  CnsReqBatchSubmitRespDto,
  CnsReqBatchTemplate,
  CnsReqBatchDto,
  CnsReqBatchRowDto,
  CnsReqBatchOutcome,
  DfNameRespDto,
  DfRespDto,
  DfRole,
  ApiKeyDto,
  DfStandingDto,
  LeaveProposalDto,
  DfRejectedDto,
  LeaveStatusDto,
  LegalHoldDto,
  NtfPreferencesDto,
  SignupApplicationDto,
  StatusNoticeDto,
  StatusReportDto,
  TermsAcceptanceDto,
  TermsStatusDto,
  AudActivityRow,
  IssuedApiKeyDto,
  AudVerifyResult,
  BilOverviewRow,
  DfSubStatusResult,
  PlnChgReqDto,
  PlnChgReqSts,
  ExtDpRefTyp,
  ProblemDetail,
  PushDvcRespDto,
  PushPltf,
  PrpsChange,
  PrpsContent,
  PrpsDefDto,
  ProcessorDto,
  ProcessorUseDto,
  PrpsSummaryDto,
  SubPln,
  UsageRespDto,
  DfInsightsDto,
  DpErasureDto,
  DpErasureSts,
  DpErasureStep,
  UsgAlertDto,
  DpReqDto,
  DpReqSts,
  DpRight,
  WebhookDeliveryDetailDto,
  WebhookDeliveryDto,
  WebhookDeliverySts,
  WebhookEndpointDto,
  WebhookWithSecretDto,
} from "./types";

export * from "./types";

export class ApiError extends Error {
  constructor(public status: number, public problem: ProblemDetail | null) {
    super(problem?.title ?? `Request failed with status ${status}`);
  }

  /** The error's stable name from the error dictionary, when the response had one: branch on this, never on the title. */
  get code(): string | undefined {
    return problemCode(this.problem);
  }
}

/** The stable code of a problem: its `code`, else the end of its `type`. */
export function problemCode(problem: Pick<ProblemDetail, "code" | "type"> | null | undefined): string | undefined {
  if (!problem) return undefined;
  if (problem.code) return problem.code;
  return problem.type?.startsWith("urn:cm:problem:") ? problem.type.slice("urn:cm:problem:".length) : undefined;
}

/**
 * What went wrong, for a person to read: the API's own explanation when it gave one, else the error's title. A failure on
 * the platform's side also carries its correlation id, so the person has a reference to give when they report it.
 */
export function friendlyError(err: unknown): string {
  if (err && typeof err === "object" && "problem" in err) {
    const p = (err as { problem?: Partial<ProblemDetail> }).problem;
    const text = p?.detail || p?.title;
    if (text) return p?.correlationId && (p.status ?? 0) >= 500 ? `${text} (reference ${p.correlationId})` : text;
  }
  if (err instanceof Error) return err.message;
  return "Something went wrong";
}

async function request<T>(baseUrl: string, path: string, token: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      // a JSON body unless it's a form upload, whose boundary the browser sets
      ...(init?.body && !(init.body instanceof FormData) ? { "Content-Type": "application/json" } : {}),
      Authorization: `Bearer ${token}`,
      ...init?.headers,
    },
  });
  if (!res.ok) {
    const problem = await res.json().catch(() => null);
    throw new ApiError(res.status, problem);
  }
  if (res.status === 204 || res.headers.get("content-length") === "0") return undefined as T;
  return (await res.json()) as T;
}

export const dfReg = {
  list: (base: string, token: string) => request<DfListItemRespDto[]>(base, "/api/df-reg/dfs", token),
  getOne: (base: string, token: string, id: string) => request<DfNameRespDto>(base, `/api/df-reg/dfs/${id}`, token),
  register: (base: string, token: string, body: DfRegisterReqDto) =>
    request<DfRespDto>(base, "/api/df-reg/dfs", token, { method: "POST", body: JSON.stringify(body) }),
  profile: (base: string, token: string, id: string) =>
    request<DfProfileRespDto>(base, `/api/df-reg/dfs/${id}/profile`, token),
  /** The logo as an image blob, or null if the fiduciary has none. */
  logo: async (base: string, token: string, id: string): Promise<Blob | null> => {
    const res = await fetch(`${base}/api/df-reg/dfs/${id}/logo`, { headers: { Authorization: `Bearer ${token}` } });
    if (res.status === 404) return null;
    if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => null));
    return res.blob();
  },
  /** A PNG or JPEG of at most 256 KB; the server checks the bytes really are that format. */
  uploadLogo: (base: string, token: string, id: string, file: Blob) =>
    request<void>(base, `/api/df-reg/dfs/${id}/logo`, token, {
      method: "PUT",
      body: file,
      headers: { "Content-Type": file.type },
    }),
  activate: (base: string, token: string, id: string) =>
    request<DfRespDto>(base, `/api/df-reg/dfs/${id}/activate`, token, { method: "POST" }),
  /** An administrator declining an application that is still waiting; the applicant is emailed the reason (ADR-065). */
  reject: (base: string, token: string, id: string, reason: string) =>
    request<DfRejectedDto>(base, `/api/df-reg/dfs/${id}/reject`, token, { method: "POST", body: JSON.stringify({ reason }) }),
  /** Platform staff stopping an active fiduciary, with the reason the team is given (ADR-064). */
  suspend: (base: string, token: string, id: string, reason: string) =>
    request<DfStandingDto>(base, `/api/df-reg/dfs/${id}/suspend`, token, { method: "POST", body: JSON.stringify({ reason }) }),
  resume: (base: string, token: string, id: string) =>
    request<DfStandingDto>(base, `/api/df-reg/dfs/${id}/resume`, token, { method: "POST" }),
  /** A legal hold stops the retention purge of a fiduciary's records until released (ADR-064). */
  placeLegalHold: (base: string, token: string, id: string, reason: string) =>
    request<LegalHoldDto>(base, `/api/df-reg/dfs/${id}/legal-hold`, token, { method: "PUT", body: JSON.stringify({ reason }) }),
  releaseLegalHold: (base: string, token: string, id: string) =>
    request<LegalHoldDto>(base, `/api/df-reg/dfs/${id}/legal-hold`, token, { method: "DELETE" }),
  /** Staff proposing to end a fiduciary's contract; a second member of staff must approve (ADR-064 D7). */
  proposeEndContract: (base: string, token: string, id: string, reason: string) =>
    request<LeaveProposalDto>(base, `/api/df-reg/dfs/${id}/end-contract`, token, { method: "POST", body: JSON.stringify({ reason }) }),
  proposeCancelEnding: (base: string, token: string, id: string, reason: string) =>
    request<LeaveProposalDto>(base, `/api/df-reg/dfs/${id}/cancel-end-contract`, token, { method: "POST", body: JSON.stringify({ reason }) }),
  leaveProposals: (base: string, token: string, status: string, dfId?: string) =>
    request<LeaveProposalDto[]>(base, `/api/df-reg/leave-proposals?status=${status}${dfId ? `&dfId=${encodeURIComponent(dfId)}` : ""}`, token),
  approveProposal: (base: string, token: string, id: string) =>
    request<LeaveProposalDto>(base, `/api/df-reg/leave-proposals/${id}/approve`, token, { method: "POST" }),
  rejectProposal: (base: string, token: string, id: string, note?: string) =>
    request<LeaveProposalDto>(base, `/api/df-reg/leave-proposals/${id}/reject`, token, { method: "POST", body: JSON.stringify({ note }) }),
  withdrawProposal: (base: string, token: string, id: string) =>
    request<LeaveProposalDto>(base, `/api/df-reg/leave-proposals/${id}/withdraw`, token, { method: "POST" }),
  /** Platform staff reading a fiduciary's acceptances of the terms, as evidence (ADR-063). */
  termsEvidence: (base: string, token: string, id: string) =>
    request<TermsAcceptanceDto[]>(base, `/api/df-reg/dfs/${id}/terms`, token),
  /** Platform staff inviting the first Owner of an active fiduciary that has none (ADR-033). */
  inviteOwner: (base: string, token: string, id: string, body: { email: string; name: string }) =>
    request<DfMemberRespDto>(base, `/api/df-reg/dfs/${id}/members`, token, {
      method: "POST",
      body: JSON.stringify({ ...body, role: "OWNER" }),
    }),
};

/** Calls that need no sign-in: an organisation applying for itself (ADR-065). */
async function publicPost<T>(baseUrl: string, path: string, body: unknown): Promise<T> {
  const res = await fetch(`${baseUrl}${path}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => null));
  return (await res.json()) as T;
}

export const signup = {
  /** Mails a link to the contact address; answers the same whether or not the address is already registered. */
  apply: (base: string, application: SignupApplicationDto) => publicPost<{ message: string }>(base, "/api/df-reg/signup", application),
  /** Confirms the contact address with the link's id and token. */
  verify: (base: string, id: string, token: string) => publicPost<{ status: string }>(base, "/api/df-reg/signup/verify", { id, token }),
};

/** The platform's status (ADR-067): public to read, posting notices is for staff. */
export const platformStatus = {
  /** No sign-in: a status page is read when sign-in is what is broken. */
  get: async (base: string): Promise<StatusReportDto> => {
    const res = await fetch(`${base}/api/status`);
    if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => null));
    return (await res.json()) as StatusReportDto;
  },
  postNotice: (base: string, token: string, body: { title: string; body: string; severity: string; componentIds: string[] }) =>
    request<StatusNoticeDto>(base, "/api/status/notices", token, { method: "POST", body: JSON.stringify(body) }),
  resolveNotice: (base: string, token: string, id: string) =>
    request<StatusNoticeDto>(base, `/api/status/notices/${encodeURIComponent(id)}/resolve`, token, { method: "POST" }),
};

/** The consent manager's terms of service and data processing agreement, for the signed-in member's fiduciary (ADR-063). */
export const terms = {
  status: (base: string, token: string) => request<TermsStatusDto[]>(base, "/api/df-reg/me/terms", token),
  /** Only an Owner accepts, and only the version in force. */
  accept: (base: string, token: string, document: string, version: string) =>
    request<TermsAcceptanceDto>(base, "/api/df-reg/me/terms", token, { method: "POST", body: JSON.stringify({ document, version }) }),
};

/** The signed-in member's own fiduciary leaving the platform (ADR-064). */
export const leaving = {
  status: (base: string, token: string) => request<LeaveStatusDto>(base, "/api/df-reg/me/leave", token),
  /** The Owner confirms by typing the organisation's name. */
  leave: (base: string, token: string, organisationName: string) =>
    request<LeaveStatusDto>(base, "/api/df-reg/me/leave", token, { method: "POST", body: JSON.stringify({ organisationName }) }),
  cancel: (base: string, token: string) => request<LeaveStatusDto>(base, "/api/df-reg/me/leave/cancel", token, { method: "POST" }),
};

/** The signed-in member's own fiduciary's team (ADR-033). */
export const dfTeam = {
  list: (base: string, token: string) => request<DfMemberRespDto[]>(base, "/api/df-reg/me/members", token),
  invite: (base: string, token: string, body: { email: string; name: string; role: DfRole }) =>
    request<DfMemberRespDto>(base, "/api/df-reg/me/members", token, { method: "POST", body: JSON.stringify(body) }),
  changeRole: (base: string, token: string, memberId: string, role: DfRole) =>
    request<DfMemberRespDto>(base, `/api/df-reg/me/members/${encodeURIComponent(memberId)}`, token, {
      method: "PUT",
      body: JSON.stringify({ role }),
    }),
  remove: (base: string, token: string, memberId: string) =>
    request<void>(base, `/api/df-reg/me/members/${encodeURIComponent(memberId)}`, token, { method: "DELETE" }),
};

export const cnsCmd = {
  create: (
    base: string,
    token: string,
    body: CnsReqBatchTemplate & {
      dpId?: string;
      extDpRefTyp?: ExtDpRefTyp;
      extDpRefVal?: string;
    },
  ) => request<CnsReqRespDto>(base, "/api/cns-cmd/cns-reqs", token, { method: "POST", body: JSON.stringify(body) }),
  /** Only the person asked can give consent: to some of the data, for a validity they choose (ADR-026). */
  approve: (base: string, token: string, cnsReqId: string, choice: GrantChoice = {}) =>
    request<CnsArtRespDto>(base, `/api/cns-cmd/cns-reqs/${cnsReqId}/approve`, token, {
      method: "POST",
      body: JSON.stringify(choice),
    }),
  /** What the request asks: its data with what refusing each means, and the validity bounds. */
  terms: (base: string, token: string, cnsReqId: string) =>
    request<CnsReqTermsDto>(base, `/api/cns-cmd/cns-reqs/${encodeURIComponent(cnsReqId)}/terms`, token),
  deny: (base: string, token: string, cnsReqId: string) =>
    request<CnsReqRespDto>(base, `/api/cns-cmd/cns-reqs/${cnsReqId}/deny`, token, { method: "POST" }),
  revoke: (base: string, token: string, cnsArtId: string) =>
    request<CnsArtRespDto>(base, `/api/cns-cmd/cns-arts/${cnsArtId}/revoke`, token, { method: "POST" }),
  /** The person ends their consent at `until`, earlier than it would (ADR-026 §4). */
  shorten: (base: string, token: string, cnsArtId: string, until: string) =>
    request<CnsArtRespDto>(base, `/api/cns-cmd/cns-arts/${encodeURIComponent(cnsArtId)}/shorten`, token, {
      method: "POST",
      body: JSON.stringify({ until }),
    }),
  /** The person stops sharing one category; the rest of the consent stays. */
  withdrawDCat: (base: string, token: string, cnsArtId: string, dCatCode: string) =>
    request<CnsArtRespDto>(base, `/api/cns-cmd/cns-arts/${encodeURIComponent(cnsArtId)}/withdraw-dcat`, token, {
      method: "POST",
      body: JSON.stringify({ dCatCode }),
    }),
  /** The person renews before it lapses: a new validity of `validityDays` from now, up to the purpose's maximum. */
  renew: (base: string, token: string, cnsArtId: string, validityDays: number) =>
    request<CnsArtRespDto>(base, `/api/cns-cmd/cns-arts/${encodeURIComponent(cnsArtId)}/renew`, token, {
      method: "POST",
      body: JSON.stringify({ validityDays }),
    }),
  // A fresh short-lived consent token for the calling fiduciary, while the consent is valid (ADR-027).
  refreshToken: (base: string, token: string, cnsArtId: string) =>
    request<{ cnsTkn: string; exp: string }>(base, `/api/cns-cmd/cns-arts/${cnsArtId}/token`, token, { method: "POST" }),
  linkDp: (base: string, token: string, body: { extDpRefTyp: ExtDpRefTyp; extDpRefVal: string }) =>
    request<{ linkedCount: number }>(base, "/api/cns-cmd/dp-links", token, {
      method: "POST",
      body: JSON.stringify(body),
    }),
};

export const cnsQry = {
  // dpId/dfId are derived server-side from the caller's own JWT, never passed as a param —
  // see CnsQryHandler.listCnsArts. Same endpoint serves both a DP's and a DF's own scope.
  listMine: (base: string, token: string) => request<CnsArtRdMdl[]>(base, "/api/cns-qry/cns-arts", token),
  dpDashboard: (base: string, token: string) =>
    request<{ dfId: string; activeCnt: number; pendingCnt: number; lastUpdatedAt: string }[]>(
      base,
      "/api/cns-qry/dp-dashboard",
      token,
    ),
  verify: (base: string, token: string, cnsArtId: string, purpose: string, cnsTkn: string) =>
    request<{ result: string }>(base, `/api/cns-qry/cns-arts/${cnsArtId}/verify?purpose=${encodeURIComponent(purpose)}`, token, {
      headers: { "X-Cns-Tkn": cnsTkn },
    }),
};

/** ISO/IEC TS 27560 consent records and signed receipts (ADR-025), for the principal and the controller. */
export const records = {
  get: (base: string, token: string, cnsReqId: string) =>
    request<ConsentRecordDoc>(base, `/api/cns-cmd/records/${encodeURIComponent(cnsReqId)}`, token),
  receipts: (base: string, token: string, cnsReqId: string) =>
    request<ReceiptView[]>(base, `/api/cns-cmd/records/${encodeURIComponent(cnsReqId)}/receipts`, token),
  /** Every record and receipt of the signed-in person, as one JSON-LD file. */
  exportAll: async (base: string, token: string): Promise<Blob> => {
    const res = await fetch(`${base}/api/cns-cmd/records/export`, { headers: { Authorization: `Bearer ${token}` } });
    if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => null));
    return res.blob();
  },
};

/** A fiduciary's purposes and their versions (ADR-031): what requests may ask for. */
export const prps = {
  list: (base: string, token: string) => request<PrpsSummaryDto[]>(base, "/api/cns-cmd/prps-defs", token),
  get: (base: string, token: string, code: string) =>
    request<PrpsDefDto>(base, `/api/cns-cmd/prps-defs/${encodeURIComponent(code)}`, token),
  /** A new purpose; its version 1 is a draft until published. */
  create: (base: string, token: string, code: string, content: PrpsContent) =>
    request<PrpsDefDto>(base, "/api/cns-cmd/prps-defs", token, { method: "POST", body: JSON.stringify({ code, content }) }),
  /** Starts or replaces the draft of the next version. */
  saveDraft: (base: string, token: string, code: string, content: PrpsContent) =>
    request<PrpsDefDto>(base, `/api/cns-cmd/prps-defs/${encodeURIComponent(code)}/draft`, token, {
      method: "PUT",
      body: JSON.stringify(content),
    }),
  discardDraft: (base: string, token: string, code: string) =>
    request<PrpsDefDto>(base, `/api/cns-cmd/prps-defs/${encodeURIComponent(code)}/draft`, token, { method: "DELETE" }),
  /** How the draft differs from the current version: whether publishing it needs re-consent. */
  draftChange: (base: string, token: string, code: string) =>
    request<PrpsChange>(base, `/api/cns-cmd/prps-defs/${encodeURIComponent(code)}/draft/change`, token),
  publish: (base: string, token: string, code: string) =>
    request<PrpsDefDto>(base, `/api/cns-cmd/prps-defs/${encodeURIComponent(code)}/publish`, token, { method: "POST" }),
};

const proc = (path: string) => `/api/cns-cmd/processors${path}`;

/** A fiduciary's register of processors, which purposes name in their recipients and transfers (ADR-075). */
export const processors = {
  list: (base: string, token: string) => request<ProcessorDto[]>(base, proc(""), token),
  create: (base: string, token: string, p: { name: string; role?: string; country?: string; contactEmail?: string }) =>
    request<ProcessorDto>(base, proc(""), token, { method: "POST", body: JSON.stringify(p) }),
  /** Changes the role, country or contact; the name stays. */
  update: (base: string, token: string, id: string, p: { role?: string; country?: string; contactEmail?: string }) =>
    request<ProcessorDto>(base, proc(`/${encodeURIComponent(id)}`), token, { method: "PUT", body: JSON.stringify(p) }),
  uses: (base: string, token: string, id: string) =>
    request<ProcessorUseDto[]>(base, proc(`/${encodeURIComponent(id)}/uses`), token),
  retire: (base: string, token: string, id: string) =>
    request<ProcessorDto>(base, proc(`/${encodeURIComponent(id)}/retire`), token, { method: "POST" }),
  reinstate: (base: string, token: string, id: string) =>
    request<ProcessorDto>(base, proc(`/${encodeURIComponent(id)}/reinstate`), token, { method: "POST" }),
};

/** Batches of consent requests (ADR-036): one template, many people, from JSON or a CSV/Excel upload. */
export const cnsBatch = {
  /** `dryRun` checks the list and reports, sending nothing. */
  submit: (base: string, token: string, body: CnsReqBatchReqDto, dryRun = false) =>
    request<CnsReqBatchSubmitRespDto>(base, `/api/cns-cmd/cns-req-batches${dryRun ? "?dryRun=true" : ""}`, token, {
      method: "POST",
      body: JSON.stringify(body),
    }),
  upload: (base: string, token: string, template: CnsReqBatchTemplate, file: File, dryRun = false) => {
    const form = new FormData();
    form.set("prpsCode", template.prpsCode);
    form.set("prpsVer", String(template.prpsVer));
    if (template.ntcLang) form.set("ntcLang", template.ntcLang);
    if (template.dCatCodes?.length) form.set("dCatCodes", template.dCatCodes.join(","));
    form.set("file", file, file.name);
    return request<CnsReqBatchSubmitRespDto>(base, `/api/cns-cmd/cns-req-batches${dryRun ? "?dryRun=true" : ""}`, token, {
      method: "POST",
      body: form,
    });
  },
  list: (base: string, token: string) => request<CnsReqBatchDto[]>(base, "/api/cns-cmd/cns-req-batches", token),
  get: (base: string, token: string, id: string) =>
    request<CnsReqBatchDto>(base, `/api/cns-cmd/cns-req-batches/${encodeURIComponent(id)}`, token),
  rows: (base: string, token: string, id: string, opts: { outcome?: CnsReqBatchOutcome; limit?: number; offset?: number } = {}) => {
    const q = new URLSearchParams({ limit: String(opts.limit ?? 100), offset: String(opts.offset ?? 0) });
    if (opts.outcome) q.set("outcome", opts.outcome);
    return request<CnsReqBatchRowDto[]>(base, `/api/cns-cmd/cns-req-batches/${encodeURIComponent(id)}/rows?${q}`, token);
  },
  /** Every row with its outcome, as a CSV file. */
  report: async (base: string, token: string, id: string): Promise<Blob> => {
    const res = await fetch(`${base}/api/cns-cmd/cns-req-batches/${encodeURIComponent(id)}/report.csv`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new ApiError(res.status, await res.json().catch(() => null));
    return res.blob();
  },
};

/** A tenant's consents for platform staff: counts, and requests with the data principal pseudonymised. */
export const cnsAdmin = {
  stats: (base: string, token: string, dfId: string) =>
    request<CnsStats>(base, `/api/cns-qry/admin/dfs/${dfId}/stats`, token),
  requests: (base: string, token: string, dfId: string, opts: { sts?: CnsLcySts; limit?: number; offset?: number } = {}) => {
    const q = new URLSearchParams();
    if (opts.sts) q.set("sts", opts.sts);
    q.set("limit", String(opts.limit ?? 25));
    q.set("offset", String(opts.offset ?? 0));
    return request<AdminCnsReqView[]>(base, `/api/cns-qry/admin/dfs/${dfId}/cns-reqs?${q}`, token);
  },
};

/** Platform staff accounts (cm-stf-svc): your own account, and staff management for administrators. */
export const stf = {
  me: (base: string, token: string) => request<StfAcct>(base, "/api/stf/me", token),
  mySessions: (base: string, token: string) => request<StfSession[]>(base, "/api/stf/me/sessions", token),
  signOutEverywhere: (base: string, token: string) =>
    request<void>(base, "/api/stf/me/sign-out-everywhere", token, { method: "POST" }),
  list: (base: string, token: string) => request<StfAcct[]>(base, "/api/stf/staff", token),
  createOperator: (base: string, token: string, body: { email: string; name: string }) =>
    request<StfAcct>(base, "/api/stf/operators", token, { method: "POST", body: JSON.stringify(body) }),
  setEnabled: (base: string, token: string, id: string, enabled: boolean) =>
    request<StfAcct>(base, `/api/stf/staff/${encodeURIComponent(id)}/enabled`, token, {
      method: "PUT",
      body: JSON.stringify({ enabled }),
    }),
};

export const ntf = {
  /** How the caller hears of new requests (ADR-066): push and an email saying one waits. */
  getPreferences: (base: string, token: string) => request<NtfPreferencesDto>(base, "/api/ntf/preferences", token),
  setPreferences: (base: string, token: string, body: { push: boolean; email: boolean }) =>
    request<NtfPreferencesDto>(base, "/api/ntf/preferences", token, { method: "PUT", body: JSON.stringify(body) }),
  // Always the caller's own devices: the DP is taken from the JWT, as in cnsQry.listMine.
  listPushDvcs: (base: string, token: string) => request<PushDvcRespDto[]>(base, "/api/ntf/push-dvcs", token),
  registerPushDvc: (base: string, token: string, body: { token: string; platform: PushPltf }) =>
    request<PushDvcRespDto>(base, "/api/ntf/push-dvcs", token, { method: "POST", body: JSON.stringify(body) }),
  removePushDvc: (base: string, token: string, id: string) =>
    request<void>(base, `/api/ntf/push-dvcs/${encodeURIComponent(id)}`, token, { method: "DELETE" }),
};

export const bil = {
  listSubPlns: (base: string, token: string) => request<SubPln[]>(base, "/api/bil/sub-plns", token),
  usage: (base: string, token: string, dfId: string) =>
    request<UsageRespDto>(base, `/api/bil/dfs/${dfId}/usage`, token),
  subStatus: (base: string, token: string, dfId: string) =>
    request<DfSubStatusResult>(base, `/api/bil/dfs/${dfId}/sub-status`, token),
  assignSubPln: (base: string, token: string, dfId: string, subPlnName: string) =>
    request<unknown>(base, `/api/bil/dfs/${dfId}/sub-pln`, token, {
      method: "PUT",
      body: JSON.stringify({ subPlnName }),
    }),
  setSubSts: (base: string, token: string, dfId: string, active: boolean) =>
    request<unknown>(base, `/api/bil/dfs/${dfId}/sub-status`, token, {
      method: "PUT",
      body: JSON.stringify({ active }),
    }),
  /** Every tenant's usage alerts for a month (yyyy-MM, this month by default), newest first (platform staff). */
  alerts: (base: string, token: string, period?: string) =>
    request<UsgAlertDto[]>(base, `/api/bil/usage-alerts${period ? `?period=${encodeURIComponent(period)}` : ""}`, token),
  /** Every tenant's plan and this month's usage, closest to its allowance first (platform staff). */
  overview: (base: string, token: string) => request<BilOverviewRow[]>(base, "/api/bil/overview", token),
  /** Plan change requests from every tenant: those waiting, or all (platform staff). */
  requests: (base: string, token: string, sts?: PlnChgReqSts) =>
    request<PlnChgReqDto[]>(base, `/api/bil/plan-change-requests${sts ? `?sts=${sts}` : ""}`, token),
  approve: (base: string, token: string, id: string, note?: string) =>
    request<PlnChgReqDto>(base, `/api/bil/plan-change-requests/${encodeURIComponent(id)}/approve`, token, {
      method: "POST",
      body: JSON.stringify({ note }),
    }),
  decline: (base: string, token: string, id: string, note: string) =>
    request<PlnChgReqDto>(base, `/api/bil/plan-change-requests/${encodeURIComponent(id)}/decline`, token, {
      method: "POST",
      body: JSON.stringify({ note }),
    }),
};

/** The audit ledger (ADR-014, ADR-040): what happened, who did it; staff see every tenant, a fiduciary its own. */
export const aud = {
  activity: (
    base: string,
    token: string,
    opts: { dfId?: string; category?: "ALL" | "CONSENT" | "PLATFORM"; before?: number; limit?: number } = {},
  ) => {
    const q = new URLSearchParams({ limit: String(opts.limit ?? 50) });
    if (opts.dfId) q.set("dfId", opts.dfId);
    if (opts.category && opts.category !== "ALL") q.set("category", opts.category);
    if (opts.before) q.set("before", String(opts.before));
    return request<AudActivityRow[]>(base, `/api/aud/activity?${q}`, token);
  },
  /** The chain and its anchored checkpoints, each with its verdict (administrators). */
  verify: (base: string, token: string) => request<AudVerifyResult>(base, "/api/aud/chain/verify", token),
  head: (base: string, token: string) => request<{ seq: number; entryHash: string }>(base, "/api/aud/chain/head", token),
};

/** A fiduciary's API keys (ADR-041): issued, listed, revoked, and whether writes must be signed. */
export const apiKeys = {
  list: (base: string, token: string) => request<ApiKeyDto[]>(base, "/api/df-reg/me/api-keys", token),
  /** The key and signing secret are in this answer only. */
  issue: (base: string, token: string, name: string, signingRequired: boolean) =>
    request<IssuedApiKeyDto>(base, "/api/df-reg/me/api-keys", token, {
      method: "POST",
      body: JSON.stringify({ name, signingRequired }),
    }),
  revoke: (base: string, token: string, id: string) =>
    request<ApiKeyDto>(base, `/api/df-reg/me/api-keys/${encodeURIComponent(id)}`, token, { method: "DELETE" }),
  setSigningRequired: (base: string, token: string, id: string, required: boolean) =>
    request<ApiKeyDto>(base, `/api/df-reg/me/api-keys/${encodeURIComponent(id)}/signing-required`, token, {
      method: "PUT",
      body: JSON.stringify({ required }),
    }),
};

/** The DF Console dashboard (ADR-049): outcomes and times to decide over the last {@code days}. */
export const insights = {
  get: (base: string, token: string, days: number) =>
    request<DfInsightsDto>(base, `/api/cns-qry/insights?days=${days}`, token),
};

const whk = (path: string) => `/api/cns-cmd/webhooks${path}`;

/** A fiduciary's webhooks (ADR-047): its endpoints and what was delivered to them. */
export const webhooks = {
  types: (base: string, token: string) => request<string[]>(base, whk("/types"), token),
  list: (base: string, token: string) => request<WebhookEndpointDto[]>(base, whk("/endpoints"), token),
  /** The signing secret is in this answer only. */
  create: (base: string, token: string, url: string, description: string, types: string[]) =>
    request<WebhookWithSecretDto>(base, whk("/endpoints"), token, {
      method: "POST",
      body: JSON.stringify({ url, description: description || null, types }),
    }),
  rotateSecret: (base: string, token: string, id: string) =>
    request<WebhookWithSecretDto>(base, whk(`/endpoints/${encodeURIComponent(id)}/rotate-secret`), token, { method: "POST" }),
  setActive: (base: string, token: string, id: string, active: boolean) =>
    request<WebhookEndpointDto>(base, whk(`/endpoints/${encodeURIComponent(id)}/${active ? "enable" : "disable"}`), token, {
      method: "POST",
    }),
  remove: (base: string, token: string, id: string) =>
    request<void>(base, whk(`/endpoints/${encodeURIComponent(id)}`), token, { method: "DELETE" }),
  test: (base: string, token: string, id: string) =>
    request<WebhookDeliveryDto>(base, whk(`/endpoints/${encodeURIComponent(id)}/test`), token, { method: "POST" }),
  deliveries: (base: string, token: string, filter: { endpointId?: string; sts?: WebhookDeliverySts; limit?: number } = {}) => {
    const q = new URLSearchParams();
    if (filter.endpointId) q.set("endpointId", filter.endpointId);
    if (filter.sts) q.set("sts", filter.sts);
    q.set("limit", String(filter.limit ?? 50));
    return request<WebhookDeliveryDto[]>(base, whk(`/deliveries?${q}`), token);
  },
  delivery: (base: string, token: string, id: string) =>
    request<WebhookDeliveryDetailDto>(base, whk(`/deliveries/${encodeURIComponent(id)}`), token),
  replay: (base: string, token: string, id: string) =>
    request<WebhookDeliveryDto>(base, whk(`/deliveries/${encodeURIComponent(id)}/replay`), token, { method: "POST" }),
};

/** A fiduciary's own billing (ADR-039): its subscription, and asking for another plan. */
export const myBilling = {
  subscription: (base: string, token: string) => request<DfSubStatusResult>(base, "/api/bil/me/subscription", token),
  requests: (base: string, token: string) => request<PlnChgReqDto[]>(base, "/api/bil/me/plan-change-requests", token),
  /** When this fiduciary's usage reached 80% and 100% of its allowance, newest first (ADR-060). */
  alerts: (base: string, token: string) => request<UsgAlertDto[]>(base, "/api/bil/me/usage-alerts", token),
  requestPlan: (base: string, token: string, subPlnName: string, note?: string) =>
    request<PlnChgReqDto>(base, "/api/bil/me/plan-change-requests", token, {
      method: "POST",
      body: JSON.stringify({ subPlnName, note }),
    }),
  withdraw: (base: string, token: string, id: string) =>
    request<PlnChgReqDto>(base, `/api/bil/me/plan-change-requests/${encodeURIComponent(id)}/withdraw`, token, {
      method: "POST",
    }),
};

/** What each right asks, in the principal's words (DPDPA ss.11-14). */
export const DP_RIGHT_LABELS: Record<DpRight, string> = {
  ACCESS: "See what they hold about me",
  CORRECTION: "Correct or update my data",
  ERASURE: "Erase my data",
  GRIEVANCE: "Complain to the organisation",
  NOMINATION: "Nominate someone to act for me",
};

export const DP_REQ_STS_LABELS: Record<DpReqSts, string> = {
  OPEN: "Received",
  ACKNOWLEDGED: "Being worked on",
  RESOLVED: "Answered",
  REFUSED: "Refused",
  WITHDRAWN: "Withdrawn",
};

const dsrPath = (prefix: string, id: string, action: string) => `${prefix}/${encodeURIComponent(id)}/${action}`;

/**
 * Principal requests (cm-dsr-svc, ADR-058). {@code me}: a principal's own requests and grievances; {@code df}: the
 * requests relayed to a fiduciary; {@code grievances}: complaints about the platform, for its staff.
 */
export const dsr = {
  me: {
    list: (base: string, token: string) => request<DpReqDto[]>(base, "/api/dsr/me/requests", token),
    relay: (base: string, token: string, cnsReqId: string, right: DpRight, details?: string) =>
      request<DpReqDto>(base, "/api/dsr/me/requests", token, {
        method: "POST",
        body: JSON.stringify({ kind: "RELAY", cnsReqId, right, details }),
      }),
    grievance: (base: string, token: string, details: string) =>
      request<DpReqDto>(base, "/api/dsr/me/requests", token, {
        method: "POST",
        body: JSON.stringify({ kind: "GRIEVANCE", details }),
      }),
    withdraw: (base: string, token: string, id: string) =>
      request<DpReqDto>(base, dsrPath("/api/dsr/me/requests", id, "withdraw"), token, { method: "POST" }),
    /** Closes the principal's account (ADR-059); confirmation is CLOSE_ACCOUNT_CONFIRMATION, typed by them. */
    closeAccount: (base: string, token: string, confirmation: string) =>
      request<DpErasureDto>(base, "/api/dsr/me/account/close", token, {
        method: "POST",
        body: JSON.stringify({ confirmation }),
      }),
    /** How far closing the account got; null when it was never asked for. */
    erasure: (base: string, token: string) => request<DpErasureDto | null>(base, "/api/dsr/me/account/close", token),
  },
  /** Every account closing, for platform staff; administrators restart a stuck one. */
  erasures: {
    list: (base: string, token: string, sts?: DpErasureSts) =>
      request<DpErasureDto[]>(base, `/api/dsr/erasures${sts ? `?sts=${sts}` : ""}`, token),
    retry: (base: string, token: string, id: string) =>
      request<DpErasureDto>(base, `/api/dsr/erasures/${encodeURIComponent(id)}/retry`, token, { method: "POST" }),
  },
  df: answerer("/api/dsr/df/requests"),
  grievances: answerer("/api/dsr/grievances"),
};

function answerer(prefix: string) {
  return {
    list: (base: string, token: string, sts?: DpReqSts) =>
      request<DpReqDto[]>(base, `${prefix}${sts ? `?sts=${sts}` : ""}`, token),
    acknowledge: (base: string, token: string, id: string) =>
      request<DpReqDto>(base, dsrPath(prefix, id, "acknowledge"), token, { method: "POST" }),
    resolve: (base: string, token: string, id: string, response: string) =>
      request<DpReqDto>(base, dsrPath(prefix, id, "resolve"), token, {
        method: "POST",
        body: JSON.stringify({ response }),
      }),
    refuse: (base: string, token: string, id: string, response: string, ground: string) =>
      request<DpReqDto>(base, dsrPath(prefix, id, "refuse"), token, {
        method: "POST",
        body: JSON.stringify({ response, ground }),
      }),
  };
}

/** What a principal types to close their account (ADR-059), so it is never done by a stray click. */
export const CLOSE_ACCOUNT_CONFIRMATION = "CLOSE MY ACCOUNT";

/** Each step of closing an account, in the words the people watching it read. */
export const ERASURE_STEP_LABELS: Record<DpErasureStep, string> = {
  NOTICE_AND_DEVICES: "Sending the notice and forgetting devices",
  CONSENTS: "Withdrawing consents and erasing contact details",
  AUDIT: "Erasing audit details",
  REQUESTS: "Withdrawing requests and erasing their words",
  IDENTITY: "Deleting the account",
};
