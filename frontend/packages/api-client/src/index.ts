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
  PrpsSummaryDto,
  SubPln,
  UsageRespDto,
} from "./types";

export * from "./types";

export class ApiError extends Error {
  constructor(public status: number, public problem: ProblemDetail | null) {
    super(problem?.title ?? `Request failed with status ${status}`);
  }
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
  /** Platform staff inviting the first Owner of an active fiduciary that has none (ADR-033). */
  inviteOwner: (base: string, token: string, id: string, body: { email: string; name: string }) =>
    request<DfMemberRespDto>(base, `/api/df-reg/dfs/${id}/members`, token, {
      method: "POST",
      body: JSON.stringify({ ...body, role: "OWNER" }),
    }),
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

/** A fiduciary's own billing (ADR-039): its subscription, and asking for another plan. */
export const myBilling = {
  subscription: (base: string, token: string) => request<DfSubStatusResult>(base, "/api/bil/me/subscription", token),
  requests: (base: string, token: string) => request<PlnChgReqDto[]>(base, "/api/bil/me/plan-change-requests", token),
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
