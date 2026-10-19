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

/**
 * The API playground's side of the sandbox (ADR-034, ADR-037, ADR-038): it only ever calls the sandbox's gateway, with
 * the sandbox keys of the signed-in person's fiduciary. Nothing here can reach production's API.
 */

export const SBX_GW_BASE: string = import.meta.env?.VITE_SBX_GW_BASE_URL ?? "http://localhost:9100";
export const SBX_INBOX_URL: string = import.meta.env?.VITE_SBX_INBOX_URL ?? "http://localhost:8125";
/** The sandbox fiduciary realm's token endpoint, for keys entered by hand (taken keys carry their own). */
export const SBX_TOKEN_URL: string =
  import.meta.env?.VITE_SBX_TOKEN_URL ?? "http://localhost:8080/realms/sbx-cm-df/protocol/openid-connect/token";

/** People who can't exist: the only contacts the sandbox takes (ADR-037). */
export const TEST_CONTACTS = ["asha@sandbox.example", "ravi@sandbox.example", "+447700900123"];

export interface SandboxKeys {
  dfId: string;
  clientId: string;
  clientSecret: string;
  tokenUrl: string;
}

export interface SandboxToken {
  accessToken: string;
  expiresAt: number;
}

/** Thrown with the sandbox's own words, when it said any. */
export class SandboxError extends Error {
  constructor(
    message: string,
    public status?: number,
  ) {
    super(message);
  }
}

async function problemOf(res: Response): Promise<string> {
  const text = await res.text();
  try {
    const body = JSON.parse(text);
    return body.detail ?? body.error_description ?? body.title ?? `The sandbox answered ${res.status}`;
  } catch {
    return text || `The sandbox answered ${res.status}`;
  }
}

/** Keys for the signed-in person's fiduciary, made the first time and with a new secret every time after. */
export async function takeKeys(productionToken: string, dfName: string): Promise<SandboxKeys & { created: boolean }> {
  let res: Response;
  try {
    res = await fetch(`${SBX_GW_BASE}/api/df-reg/sandbox-keys`, {
      method: "POST",
      headers: { Authorization: `Bearer ${productionToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ dfName }),
    });
  } catch {
    throw new SandboxError(`The sandbox isn't reachable at ${SBX_GW_BASE}. Is it running?`);
  }
  if (!res.ok) throw new SandboxError(await problemOf(res), res.status);
  return res.json();
}

/** A sandbox token for the keys, from the sandbox's own realm. */
export async function sandboxToken(keys: SandboxKeys): Promise<SandboxToken> {
  const res = await fetch(keys.tokenUrl, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "client_credentials", client_id: keys.clientId, client_secret: keys.clientSecret }),
  });
  if (!res.ok) throw new SandboxError(await problemOf(res), res.status);
  const body = await res.json();
  return { accessToken: body.access_token, expiresAt: Date.now() + (body.expires_in ?? 300) * 1000 };
}

// ---- the catalogue ----

export type Body = { kind: "none" } | { kind: "json"; sample: string } | { kind: "upload"; sample: string };

export interface Operation {
  id: string;
  group: string;
  title: string;
  method: "GET" | "POST";
  /** With {placeholders} for path parameters. */
  path: string;
  about: string;
  body: Body;
  /** Called without a token. */
  open?: boolean;
  /** Ids in its response that later calls' {placeholders} can use, such as the batch it created. */
  remembers?: (body: any) => Record<string, string | undefined>;
}

/** What every request sample asks (ADR-031): version 1 of the purpose the first samples define and publish. */
const ASK = { prpsCode: "ORDER_UPDATES", prpsVer: 1 };

const PURPOSE = {
  code: "ORDER_UPDATES",
  content: {
    name: "Order updates",
    description: "Emails about the orders you place with us.",
    dCats: [
      {
        code: "EMAIL",
        dpv: "dpv-pd:EmailAddress",
        sensitivity: "ORDINARY",
        necessary: true,
        consequence: "Without your email address we can't tell you when your order ships.",
      },
    ],
    recipients: [],
    transfers: [],
    retentionDays: 365,
    requestedValidityDays: 180,
    maxValidityDays: 365,
    flags: [],
    notices: [
      {
        lang: "en",
        title: "Order updates by email",
        text: "We email you when your order ships, for as long as you shop with us. Nobody else sees your address. You can withdraw at any time.",
      },
    ],
  },
};

const json = (value: unknown) => JSON.stringify(value, null, 2);

export const OPERATIONS: Operation[] = [
  {
    id: "purpose",
    group: "Purposes",
    title: "Define a purpose",
    method: "POST",
    path: "/api/cns-cmd/prps-defs",
    about: "What you ask consent for: the data, how long, who else receives it, and the notice. Version 1 is a draft.",
    remembers: (b) => ({ prpsCode: b?.code }),
    body: { kind: "json", sample: json(PURPOSE) },
  },
  {
    id: "purpose-publish",
    group: "Purposes",
    title: "Publish it",
    method: "POST",
    path: "/api/cns-cmd/prps-defs/{prpsCode}/publish",
    about: "The draft becomes the current version. Requests name it as prpsCode and prpsVer; it never changes again.",
    body: { kind: "none" },
  },
  {
    id: "purposes",
    group: "Purposes",
    title: "Your purposes",
    method: "GET",
    path: "/api/cns-cmd/prps-defs",
    about: "Every purpose with its current version, and any draft.",
    body: { kind: "none" },
    remembers: (b) => ({ prpsCode: Array.isArray(b) ? b[0]?.code : undefined }),
  },
  {
    id: "raise",
    group: "Consent requests",
    title: "Ask one person",
    method: "POST",
    path: "/api/cns-cmd/cns-reqs",
    about:
      "Raises a consent request for a published purpose version. The person is notified and decides in their portal.",
    remembers: (b) => ({ requestId: b?.id }),
    body: { kind: "json", sample: json({ extDpRefTyp: "EMAIL", extDpRefVal: TEST_CONTACTS[0], ...ASK }) },
  },
  {
    id: "list",
    group: "Consent requests",
    title: "Your requests and consents",
    method: "GET",
    path: "/api/cns-qry/cns-arts",
    about: "Every request your organisation raised, with where it stands.",
    body: { kind: "none" },
    remembers: (b) => ({ requestId: Array.isArray(b) ? b[0]?.cnsReqId : undefined }),
  },
  {
    id: "one",
    group: "Consent requests",
    title: "One request",
    method: "GET",
    path: "/api/cns-qry/cns-arts/{requestId}",
    about: "A request and, once given, its consent.",
    body: { kind: "none" },
  },
  {
    id: "history",
    group: "Consent requests",
    title: "A request's history",
    method: "GET",
    path: "/api/cns-qry/cns-arts/{requestId}/history",
    about: "Each change of status, oldest first.",
    body: { kind: "none" },
  },
  {
    id: "batch-check",
    group: "Many people at once",
    title: "Check a list (dry run)",
    method: "POST",
    path: "/api/cns-cmd/cns-req-batches?dryRun=true",
    about: "Reads the list and reports valid, invalid and repeated rows, and what your plan has left. Sends nothing.",
    body: {
      kind: "json",
      sample: json({
        ...ASK,
        recipients: [
          { email: TEST_CONTACTS[0], ref: "CUST-001" },
          { phone: TEST_CONTACTS[2], ref: "CUST-002" },
          { contact: "not-an-address", ref: "CUST-003" },
        ],
      }),
    },
  },
  {
    id: "batch",
    group: "Many people at once",
    title: "Send to a list",
    method: "POST",
    path: "/api/cns-cmd/cns-req-batches",
    about: "One request for everyone on the list, sent in the background. Up to 10,000 people.",
    remembers: (b) => ({ batchId: b?.batch?.id }),
    body: {
      kind: "json",
      sample: json({ ...ASK, recipients: [{ email: TEST_CONTACTS[0], ref: "CUST-001" }, { email: TEST_CONTACTS[1], ref: "CUST-002" }] }),
    },
  },
  {
    id: "batch-upload",
    group: "Many people at once",
    title: "Send a CSV list",
    method: "POST",
    path: "/api/cns-cmd/cns-req-batches",
    about: "The same, as a file upload (multipart): the request's fields, and the list as CSV (an .xlsx works too).",
    remembers: (b) => ({ batchId: b?.batch?.id }),
    body: {
      kind: "upload",
      sample: json({
        ...ASK,
        file: `Email,Customer ID\n${TEST_CONTACTS[0]},CUST-001\n${TEST_CONTACTS[1]},CUST-002\n`,
      }),
    },
  },
  {
    id: "batches",
    group: "Many people at once",
    title: "Your lists",
    method: "GET",
    path: "/api/cns-cmd/cns-req-batches",
    about: "Every list sent, newest first.",
    body: { kind: "none" },
    remembers: (b) => ({ batchId: Array.isArray(b) ? b[0]?.id : undefined }),
  },
  {
    id: "batch-one",
    group: "Many people at once",
    title: "One list's progress",
    method: "GET",
    path: "/api/cns-cmd/cns-req-batches/{batchId}",
    about: "Counts of sent, waiting, invalid and repeated rows.",
    body: { kind: "none" },
  },
  {
    id: "batch-rows",
    group: "Many people at once",
    title: "One list's rows",
    method: "GET",
    path: "/api/cns-cmd/cns-req-batches/{batchId}/rows?outcome=INVALID",
    about: "Each row's outcome; outcome is SENT, PENDING, INVALID, DUPLICATE or FAILED, or leave it out for all.",
    body: { kind: "none" },
  },
  {
    id: "jwks",
    group: "Consent tokens",
    title: "Public keys",
    method: "GET",
    path: "/api/cns-cmd/.well-known/jwks.json",
    about: "The keys consent tokens are signed with, to verify them yourself. The sandbox's differ from production's.",
    body: { kind: "none" },
    open: true,
  },
];

/** The {placeholders} in a path, in order. */
export const pathParams = (path: string) => Array.from(path.matchAll(/\{(\w+)\}/g), (m) => m[1]);

/** The path with each placeholder filled, encoded; null while one is still empty. */
export function fillPath(path: string, values: Record<string, string>): string | null {
  let missing = false;
  const filled = path.replace(/\{(\w+)\}/g, (_, name: string) => {
    const value = values[name]?.trim();
    if (!value) missing = true;
    return encodeURIComponent(value ?? "");
  });
  return missing ? null : filled;
}

/** What a response gives later calls, leaving out what it didn't have. */
export function remembered(op: Operation, body: unknown): Record<string, string> {
  const ids = op.remembers?.(body) ?? {};
  return Object.fromEntries(Object.entries(ids).filter((e): e is [string, string] => typeof e[1] === "string" && e[1] !== ""));
}

/** An upload body as the form it is sent as: every field as text, and {@code file} as a CSV file. */
export function uploadForm(text: string): FormData {
  const fields = JSON.parse(text) as Record<string, unknown>;
  const form = new FormData();
  for (const [name, value] of Object.entries(fields)) {
    if (name === "file") form.set("file", new Blob([String(value)], { type: "text/csv" }), "list.csv");
    else form.set(name, Array.isArray(value) ? value.join(",") : String(value));
  }
  return form;
}

const quote = (s: string) => `'${s.replace(/'/g, `'\\''`)}'`;

/** The same call as a shell command; the token stays a variable, never pasted in. */
export function asCurl(op: Operation, path: string, body: string): string {
  const lines = [`curl -X ${op.method} ${quote(SBX_GW_BASE + path)}`];
  if (!op.open) lines.push(`-H "Authorization: Bearer $SANDBOX_TOKEN"`);
  if (op.body.kind === "json") {
    lines.push(`-H 'Content-Type: application/json'`, `--data-raw ${quote(body)}`);
  } else if (op.body.kind === "upload") {
    try {
      for (const [name, value] of Object.entries(JSON.parse(body) as Record<string, unknown>)) {
        lines.push(name === "file" ? `-F 'file=@list.csv;type=text/csv'` : `-F ${quote(`${name}=${Array.isArray(value) ? value.join(",") : String(value)}`)}`);
      }
    } catch {
      lines.push("# the body isn't valid JSON");
    }
  }
  return lines.join(" \\\n  ");
}

/** How a system gets the token the calls use; the secret stays a variable too. */
export function tokenCurl(keys: Pick<SandboxKeys, "clientId" | "tokenUrl">): string {
  return [
    `SANDBOX_TOKEN=$(curl -s -X POST ${quote(keys.tokenUrl)}`,
    `-d grant_type=client_credentials -d client_id=${keys.clientId}`,
    `-d "client_secret=$SANDBOX_CLIENT_SECRET" | jq -r .access_token)`,
  ].join(" \\\n  ");
}
