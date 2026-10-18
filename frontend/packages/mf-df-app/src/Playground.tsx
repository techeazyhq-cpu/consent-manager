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

import { useMemo, useState } from "react";
import { Button, Card, ErrorBanner, Field, PageHeader } from "ui-kit/src/index";
import type { Console } from "./console";
import { usesSandbox } from "./roles";
import {
  OPERATIONS,
  SBX_GW_BASE,
  SBX_INBOX_URL,
  SBX_TOKEN_URL,
  SandboxError,
  TEST_CONTACTS,
  asCurl,
  fillPath,
  pathParams,
  remembered,
  sandboxToken,
  takeKeys,
  tokenCurl,
  uploadForm,
  type Operation,
  type SandboxKeys,
  type SandboxToken,
} from "./sandbox";
import { freshToken } from "./token";

const KEYS_STORE = "cm-df-sandbox-keys";

/** Kept for this browser tab only, so a reload doesn't lose them; gone when the tab closes. */
function loadKeys(dfId: string): SandboxKeys | null {
  try {
    const keys = JSON.parse(sessionStorage.getItem(KEYS_STORE) ?? "null") as SandboxKeys | null;
    return keys && keys.dfId === dfId ? keys : null;
  } catch {
    return null;
  }
}

function saveKeys(keys: SandboxKeys | null) {
  try {
    if (keys) sessionStorage.setItem(KEYS_STORE, JSON.stringify(keys));
    else sessionStorage.removeItem(KEYS_STORE);
  } catch {
    // private windows may refuse storage; the keys still work until the page reloads
  }
}

interface Answer {
  status: number;
  ms: number;
  contentType: string;
  body: string;
}

function pretty(text: string): string {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
}

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // the clipboard can be refused; the text is on screen to copy by hand
  }
}

function KeysCard({ console: c, keys, onKeys }: { console: Console; keys: SandboxKeys | null; onKeys: (k: SandboxKeys | null) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [shown, setShown] = useState(false);
  const [pasteId, setPasteId] = useState("");
  const [pasteSecret, setPasteSecret] = useState("");
  const mayTake = usesSandbox(c.role);

  const take = async () => {
    setBusy(true);
    setError(null);
    try {
      const issued = await takeKeys(await freshToken(c.auth), c.dfName);
      onKeys({ dfId: issued.dfId, clientId: issued.clientId, clientSecret: issued.clientSecret, tokenUrl: issued.tokenUrl });
      setShown(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <h3 className="cm-section-title" style={{ marginTop: 0 }}>
        Your sandbox keys
      </h3>
      {error ? <ErrorBanner message={error} /> : null}
      {keys ? (
        <>
          <div className="cm-fields">
            <div>
              <span className="cm-field-label">Client ID</span>
              <code className="df-code-inline">{keys.clientId}</code>
            </div>
            <div>
              <span className="cm-field-label">Client secret</span>
              <code className="df-code-inline">{shown ? keys.clientSecret : "•".repeat(16)}</code>{" "}
              <button className="df-link-button" onClick={() => setShown(!shown)}>
                {shown ? "Hide" : "Show"}
              </button>{" "}
              <button className="df-link-button" onClick={() => copy(keys.clientSecret)}>
                Copy
              </button>
            </div>
          </div>
          <p className="cm-note">
            Kept in this tab only. Store the secret somewhere safe if your systems will use it: taking keys again replaces it,
            and the old one stops working.
          </p>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {mayTake ? (
              <Button onClick={take} disabled={busy}>
                {busy ? "Replacing…" : "Replace the secret"}
              </Button>
            ) : null}
            <Button onClick={() => onKeys(null)}>Forget them here</Button>
          </div>
        </>
      ) : mayTake ? (
        <>
          <p className="cm-note" style={{ marginTop: 0 }}>
            The sandbox has a copy of your organisation, made the first time you take keys. Only test contacts can be asked
            there, and nothing is delivered.
          </p>
          <Button variant="primary" onClick={take} disabled={busy}>
            {busy ? "Taking keys…" : "Get sandbox keys"}
          </Button>
        </>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (pasteId.trim() && pasteSecret.trim()) {
              onKeys({ dfId: c.dfId, clientId: pasteId.trim(), clientSecret: pasteSecret.trim(), tokenUrl: SBX_TOKEN_URL });
            }
          }}
        >
          <p className="cm-note" style={{ marginTop: 0 }}>
            An Owner, Administrator or Developer takes your organisation's sandbox keys. If one shared them with you, enter
            them here.
          </p>
          <div className="cm-form-grid">
            <Field label="Client ID">
              <input value={pasteId} onChange={(e) => setPasteId(e.target.value)} placeholder="df-api-…" />
            </Field>
            <Field label="Client secret">
              <input type="password" value={pasteSecret} onChange={(e) => setPasteSecret(e.target.value)} />
            </Field>
          </div>
          <Button type="submit">Use these keys</Button>
        </form>
      )}
    </Card>
  );
}

/**
 * The API playground (ADR-034, ADR-038): try every call a fiduciary's systems make, against the sandbox only, and see
 * the request and the answer. Each call can be copied as a curl command for your own code.
 */
export default function Playground({ console: c }: { console: Console }) {
  const [keys, setKeys] = useState<SandboxKeys | null>(() => loadKeys(c.dfId));
  const [token, setToken] = useState<SandboxToken | null>(null);
  const [opId, setOpId] = useState(OPERATIONS[0].id);
  const [bodies, setBodies] = useState<Record<string, string>>({});
  const [params, setParams] = useState<Record<string, string>>({});
  const [answer, setAnswer] = useState<Answer | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const op = OPERATIONS.find((o) => o.id === opId) ?? OPERATIONS[0];
  const body = bodies[op.id] ?? (op.body.kind === "none" ? "" : op.body.sample);
  const names = pathParams(op.path);
  const path = fillPath(op.path, params);
  const groups = useMemo(() => Array.from(new Set(OPERATIONS.map((o) => o.group))), []);

  const useKeys = (next: SandboxKeys | null) => {
    setKeys(next);
    setToken(null);
    saveKeys(next);
  };

  const pick = (next: Operation) => {
    setOpId(next.id);
    setAnswer(null);
    setError(null);
  };

  const send = async () => {
    if (!path) {
      setError(`Fill in ${names.filter((n) => !params[n]?.trim()).join(" and ")} first.`);
      return;
    }
    if (!op.open && !keys) {
      setError("Get your sandbox keys first.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      let current = token;
      if (!op.open && keys && (!current || current.expiresAt < Date.now() + 10_000)) {
        current = await sandboxToken(keys);
        setToken(current);
      }
      const headers: Record<string, string> = {};
      if (!op.open && current) headers.Authorization = `Bearer ${current.accessToken}`;
      let payload: BodyInit | undefined;
      if (op.body.kind === "json") {
        headers["Content-Type"] = "application/json";
        payload = body;
      } else if (op.body.kind === "upload") {
        payload = uploadForm(body);
      }
      const started = performance.now();
      const res = await fetch(SBX_GW_BASE + path, { method: op.method, headers, body: payload });
      const text = await res.text();
      const ms = Math.round(performance.now() - started);
      setAnswer({ status: res.status, ms, contentType: res.headers.get("Content-Type") ?? "", body: text });
      try {
        const ids = remembered(op, JSON.parse(text));
        if (Object.keys(ids).length) setParams((prev) => ({ ...prev, ...ids }));
      } catch {
        // not JSON: nothing to remember
      }
    } catch (err) {
      if (err instanceof SandboxError && err.status === 401) {
        setError("The sandbox refused these keys. Take new ones: after a sandbox reset, or when someone replaced the secret, the old ones stop working.");
      } else if (err instanceof SyntaxError) {
        setError("The body isn't valid JSON.");
      } else {
        setError(err instanceof Error ? err.message : String(err));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="API playground"
        subtitle="Try the API your systems will call, against the sandbox: test people only, nothing delivered, nothing of your real data."
      />
      <div className="df-callout">
        <span>
          <strong>Sandbox</strong> · {SBX_GW_BASE} · test contacts only, such as {TEST_CONTACTS.join(", ")}
        </span>
        <a href={SBX_INBOX_URL} target="_blank" rel="noreferrer">
          The sandbox's inbox
        </a>
      </div>
      <KeysCard console={c} keys={keys} onKeys={useKeys} />

      <div className="df-playground">
        <nav aria-label="API calls" className="df-ops">
          {groups.map((g) => (
            <div key={g}>
              <div className="df-ops-group">{g}</div>
              {OPERATIONS.filter((o) => o.group === g).map((o) => (
                <button key={o.id} aria-current={o.id === op.id ? "true" : undefined} onClick={() => pick(o)}>
                  <span className={`df-method df-method-${o.method.toLowerCase()}`}>{o.method}</span> {o.title}
                </button>
              ))}
            </div>
          ))}
        </nav>

        <div>
          <Card>
            <div className="df-request-line">
              <span className={`df-method df-method-${op.method.toLowerCase()}`}>{op.method}</span>
              <code>{path ?? op.path}</code>
            </div>
            <p className="cm-note">{op.about}</p>
            {names.length ? (
              <div className="cm-form-grid">
                {names.map((n) => (
                  <Field key={n} label={n}>
                    <input
                      value={params[n] ?? ""}
                      onChange={(e) => setParams({ ...params, [n]: e.target.value })}
                      placeholder="Filled in from earlier answers"
                    />
                  </Field>
                ))}
              </div>
            ) : null}
            {op.body.kind !== "none" ? (
              <Field label={op.body.kind === "upload" ? "Form fields (file: the CSV list)" : "Body (JSON)"}>
                <textarea
                  className="df-code"
                  rows={Math.min(18, body.split("\n").length + 1)}
                  spellCheck={false}
                  value={body}
                  onChange={(e) => setBodies({ ...bodies, [op.id]: e.target.value })}
                />
              </Field>
            ) : null}
            {error ? <ErrorBanner message={error} /> : null}
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <Button variant="primary" onClick={send} disabled={busy}>
                {busy ? "Sending…" : "Send"}
              </Button>
              {op.body.kind !== "none" && bodies[op.id] !== undefined ? (
                <Button onClick={() => setBodies({ ...bodies, [op.id]: (op.body as { sample: string }).sample })}>Reset the body</Button>
              ) : null}
              <Button onClick={() => copy(asCurl(op, path ?? op.path, body))}>Copy as curl</Button>
            </div>
          </Card>

          {answer ? (
            <Card style={{ marginTop: 16 }}>
              <div className="df-request-line">
                <span className={`cm-badge ${answer.status < 300 ? "cm-badge-active" : answer.status < 500 ? "cm-badge-pending" : "cm-badge-denied"}`}>
                  {answer.status}
                </span>
                <span className="cm-note" style={{ margin: 0 }}>
                  {answer.ms} ms · {answer.contentType || "no body"}
                </span>
              </div>
              <pre className="df-code" aria-label="Response">
                {answer.body ? pretty(answer.body) : "(empty)"}
              </pre>
            </Card>
          ) : null}

          <details className="df-howto">
            <summary>From your own code</summary>
            <p className="cm-note">Get a token with your keys, then call the sandbox the same way. Keep the secret out of code and logs.</p>
            <pre className="df-code">{`export SANDBOX_CLIENT_SECRET=…   # from "Your sandbox keys"\n${tokenCurl(keys ?? { clientId: "df-api-…", tokenUrl: SBX_TOKEN_URL })}\n\n${asCurl(op, path ?? op.path, body)}`}</pre>
          </details>
        </div>
      </div>
    </>
  );
}
