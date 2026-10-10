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
  DfNameRespDto,
  DfRespDto,
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
  register: (base: string, token: string, body: { name: string; contactEmail: string }) =>
    request<DfRespDto>(base, "/api/df-reg/dfs", token, { method: "POST", body: JSON.stringify(body) }),
  activate: (base: string, token: string, id: string) =>
    request<DfRespDto>(base, `/api/df-reg/dfs/${id}/activate`, token, { method: "POST" }),
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

/**
 * DF Integration API auth (plan §10): a DF operator authenticates directly against Keycloak's
 * token endpoint with the client-credentials secret issued at activation — the same grant type
 * already proven end-to-end in scripts/verify-phase1.sh, reused here so mf-df-app needs no
 * Keycloak realm changes to support interactive DF staff logins.
 */
export async function fetchDfClientCredentialsToken(
  kcBase: string,
  realm: string,
  dfId: string,
  clientSecret: string,
): Promise<string> {
  const res = await fetch(`${kcBase}/realms/${realm}/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "client_credentials",
      client_id: `df-api-${dfId}`,
      client_secret: clientSecret,
    }),
  });
  if (!res.ok) throw new ApiError(res.status, null);
  const body = (await res.json()) as { access_token: string };
  return body.access_token;
}
