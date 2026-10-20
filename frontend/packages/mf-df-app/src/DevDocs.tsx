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

import { Card, PageHeader } from "ui-kit/src/index";
import type { Console } from "./console";
import { WEBHOOK_VERIFY_NODE, connectionFor, quickStart } from "./connect";

const KC_BASE = import.meta.env?.VITE_KC_BASE_URL ?? "http://localhost:8080";
const KC_REALM = import.meta.env?.VITE_KC_REALM ?? "cm-df";

type Text = Promise<{ default: string }>;

/** The contracts, shipped with the console so they always match the API it calls; each loaded only when asked for. */
const DOWNLOADS: { name: string; what: string; type: string; load: () => Text }[] = [
  {
    name: "df-api.openapi.yaml",
    what: "The API (OpenAPI 3.1): every path, schema and error",
    type: "application/yaml",
    load: () => import("../../../../docs/api/df-api.openapi.yaml?raw") as Text,
  },
  {
    name: "df-webhooks.asyncapi.yaml",
    what: "What webhooks post to you (AsyncAPI 3)",
    type: "application/yaml",
    load: () => import("../../../../docs/api/df-webhooks.asyncapi.yaml?raw") as Text,
  },
  {
    name: "df-api.postman_collection.json",
    what: "Every call for Postman, signed for you",
    type: "application/json",
    load: () => import("../../../../docs/api/df-api.postman_collection.json?raw") as Text,
  },
  {
    name: "df-api.local.postman_environment.json",
    what: "A Postman environment to fill in",
    type: "application/json",
    load: () => import("../../../../docs/api/df-api.local.postman_environment.json?raw") as Text,
  },
];

async function download(name: string, load: () => Text, type: string) {
  const text = (await load()).default;
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function Code({ children }: { children: string }) {
  return (
    <div style={{ position: "relative" }}>
      <pre className="df-code">{children}</pre>
      <button
        className="df-link-button df-copy"
        onClick={() => navigator.clipboard?.writeText(children).catch(() => undefined)}
      >
        Copy
      </button>
    </div>
  );
}

/**
 * Developer docs (ADR-048): how this organisation's systems connect, a quick start filled in with its own details,
 * checking webhooks, and the API contracts to download. Open to every member: nothing here is secret.
 */
export default function DevDocs({ console: c }: { console: Console }) {
  const conn = connectionFor(c.dfId, c.gwBase, KC_BASE, KC_REALM);
  return (
    <>
      <PageHeader
        title="Developer docs"
        subtitle="Connect your systems: ask for consent, follow it, and check it before you use anyone's data."
      />

      <Card>
        <h3 className="cm-section-title" style={{ marginTop: 0 }}>
          Your connection
        </h3>
        <dl className="df-conn">
          <dt>API base URL</dt>
          <dd>
            <code className="df-code-inline">{conn.gwBase}</code>
          </dd>
          <dt>Token URL</dt>
          <dd>
            <code className="df-code-inline">{conn.tokenUrl}</code> (OAuth 2.0 client credentials)
          </dd>
          <dt>Client id</dt>
          <dd>
            <code className="df-code-inline">{conn.clientId}</code>
          </dd>
        </dl>
        <p className="cm-note">
          Or call with an <a href="#/api-keys">API key</a> in <code>X-API-Key</code>, signing each write with its secret.
          Try everything first, with test people only, in the <a href="#/playground">API playground</a>.
        </p>
      </Card>

      <h3 className="cm-section-title">Quick start</h3>
      <p className="cm-note">
        First <a href="#/purposes">define and publish a purpose</a>: what you need the data for, and the notice people
        read. Then:
      </p>
      {quickStart(conn).map((s) => (
        <section key={s.title} className="df-doc-step">
          <h4>{s.title}</h4>
          <p className="cm-note">{s.text}</p>
          <Code>{s.code}</Code>
        </section>
      ))}

      <h3 className="cm-section-title">Webhooks</h3>
      <p className="cm-note">
        Add an endpoint on the <a href="#/webhooks">Webhooks</a> page. Check every message's signature with its secret
        before acting on it, answer 2xx within 10 seconds, and ignore a <code>webhook-id</code> you've already handled.
        The Standard Webhooks libraries do this for you; by hand, in Node.js:
      </p>
      <Code>{WEBHOOK_VERIFY_NODE}</Code>

      <h3 className="cm-section-title">Download the contracts</h3>
      <div style={{ overflowX: "auto" }}>
        <table className="cm-table">
          <tbody>
            {DOWNLOADS.map((d) => (
              <tr key={d.name}>
                <td>
                  <code>{d.name}</code>
                </td>
                <td>{d.what}</td>
                <td>
                  <button className="df-link-button" onClick={() => download(d.name, d.load, d.type)}>
                    Download
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
