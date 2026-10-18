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
  DfListItemRespDto,
  DfMemberRespDto,
  DfProfileRespDto,
  DfRegisterReqDto,
  AdminCnsReqView,
  CnsLcySts,
  CnsStats,
  StfAcct,
  StfSession,
  DfNameRespDto,
  DfRespDto,
  DfRole,
  DfSubStatusResult,
  ExtDpRefTyp,
  ProblemDetail,
  PushDvcRespDto,
  PushPltf,
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
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
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
    body: {
      dpId?: string;
      extDpRefTyp?: ExtDpRefTyp;
      extDpRefVal?: string;
      prpsCode: string;
      dCatCodes: string[];
      ntcTitle: string;
      ntcText: string;
    },
  ) => request<CnsReqRespDto>(base, "/api/cns-cmd/cns-reqs", token, { method: "POST", body: JSON.stringify(body) }),
  approve: (base: string, token: string, cnsReqId: string) =>
    request<CnsArtRespDto>(base, `/api/cns-cmd/cns-reqs/${cnsReqId}/approve`, token, { method: "POST" }),
  deny: (base: string, token: string, cnsReqId: string) =>
    request<CnsReqRespDto>(base, `/api/cns-cmd/cns-reqs/${cnsReqId}/deny`, token, { method: "POST" }),
  revoke: (base: string, token: string, cnsArtId: string) =>
    request<CnsArtRespDto>(base, `/api/cns-cmd/cns-arts/${cnsArtId}/revoke`, token, { method: "POST" }),
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
};
