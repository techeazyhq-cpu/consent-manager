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

import { useCallback, useEffect, useState } from "react";
import { apiKeys, type ApiKeyDto, type IssuedApiKeyDto } from "api-client";
import { Badge, Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import type { Console } from "./console";
import { fmtDate } from "./labels";
import { managesApiKeys } from "./roles";
import { freshToken } from "./token";

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // the clipboard can be refused; the text is on screen to copy by hand
  }
}

/** The key and secret, shown this once and never again. */
function Issued({ issued, onDone }: { issued: IssuedApiKeyDto; onDone: () => void }) {
  return (
    <Card>
      <h3 className="cm-section-title" style={{ marginTop: 0 }}>
        “{issued.key.name}” is ready: copy it now
      </h3>
      <p className="cm-note">
        This is the only time the key and signing secret are shown. Keep them in your secrets manager, never in code or
        logs.
      </p>
      <div className="cm-fields">
        <div>
          <span className="cm-field-label">API key (X-API-Key)</span>
          <code className="df-code-inline">{issued.apiKey}</code>{" "}
          <button className="df-link-button" onClick={() => copy(issued.apiKey)}>
            Copy
          </button>
        </div>
        <div>
          <span className="cm-field-label">Signing secret (HMAC-SHA256)</span>
          <code className="df-code-inline">{issued.signingSecret}</code>{" "}
          <button className="df-link-button" onClick={() => copy(issued.signingSecret)}>
            Copy
          </button>
        </div>
      </div>
      <Button variant="primary" onClick={onDone}>
        I've stored them
      </Button>
    </Card>
  );
}

/**
 * API keys (ADR-041): what the organisation's systems call the API with, instead of OAuth client credentials if they
 * prefer. Each key has a signing secret; with signing required, writes must be signed (CM1-HMAC-SHA256) and each
 * signed request can be used once. Owners, Administrators and Developers manage them.
 */
export default function ApiKeys({ console: c }: { console: Console }) {
  const [keys, setKeys] = useState<ApiKeyDto[] | null>(null);
  const [name, setName] = useState("");
  const [required, setRequired] = useState(true);
  const [issued, setIssued] = useState<IssuedApiKeyDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setKeys(await apiKeys.list(c.gwBase, await freshToken(c.auth)));
    } catch (err) {
      setError(friendlyError(err));
      setKeys((prev) => prev ?? []);
    }
  }, [c.auth, c.gwBase]);

  useEffect(() => {
    if (managesApiKeys(c.role)) load();
  }, [load, c.role]);

  const run = async (action: (token: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action(await freshToken(c.auth));
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  if (!managesApiKeys(c.role)) {
    return (
      <>
        <PageHeader title="API keys" />
        <p className="cm-note">Owners, Administrators and Developers manage the organisation's API keys.</p>
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="API keys"
        subtitle="For your systems to call the API with a key, and signed requests that can't be replayed or altered."
      />
      {error ? <ErrorBanner message={error} /> : null}
      {issued ? (
        <Issued issued={issued} onDone={() => setIssued(null)} />
      ) : (
        <Card>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              await run(async (token) => {
                setIssued(await apiKeys.issue(c.gwBase, token, name.trim(), required));
                setName("");
              });
            }}
          >
            <div className="cm-form-grid">
              <Field label="Name">
                <input value={name} maxLength={64} onChange={(e) => setName(e.target.value)} placeholder="e.g. Orders backend" />
              </Field>
              <Field label="Signed requests">
                <label style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <input type="checkbox" checked={required} onChange={(e) => setRequired(e.target.checked)} />
                  Require a signature on every write
                </label>
              </Field>
            </div>
            <Button type="submit" variant="primary" disabled={busy || !name.trim()}>
              {busy ? "Issuing…" : "Issue a key"}
            </Button>
          </form>
          <p className="cm-note">
            Sign each request with HMAC-SHA256 over the method, path, query, body hash, a timestamp and a new nonce, as the{" "}
            <a href="#/playground">API playground</a> and the Postman collection do. A signed request is accepted once,
            within five minutes of its timestamp.
          </p>
        </Card>
      )}

      <h3 className="cm-section-title">Keys</h3>
      {keys === null ? (
        <Spinner />
      ) : keys.length === 0 ? (
        <p className="cm-note">No API keys yet.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>Key</th>
                <th>Signed writes</th>
                <th>Issued</th>
                <th>Last used</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {keys.map((k) => (
                <tr key={k.id}>
                  <td>{k.name}</td>
                  <td>
                    <code>{k.keyPrefix}…</code>
                  </td>
                  <td>
                    {k.revokedAt ? (
                      <Badge status="REVOKED" />
                    ) : (
                      <label style={{ display: "flex", gap: 6, alignItems: "center" }}>
                        <input
                          type="checkbox"
                          checked={k.signingRequired}
                          disabled={busy}
                          onChange={(e) => run((token) => apiKeys.setSigningRequired(c.gwBase, token, k.id, e.target.checked))}
                        />
                        Required
                      </label>
                    )}
                  </td>
                  <td>{fmtDate(k.createdAt)}</td>
                  <td>{fmtDate(k.lastUsedAt)}</td>
                  <td>
                    {k.revokedAt ? null : (
                      <Button
                        variant="danger"
                        disabled={busy}
                        onClick={() => {
                          if (window.confirm(`Revoke “${k.name}”? Systems using it stop working at once.`)) {
                            run((token) => apiKeys.revoke(c.gwBase, token, k.id));
                          }
                        }}
                      >
                        Revoke
                      </Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
