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

import { useState } from "react";
import type { ExtDpRefTyp } from "api-client";
import { Button, Card, ErrorBanner, Field, PageHeader, humanise } from "ui-kit/src/index";
import type { Console, NewRequest } from "./console";
import { purposeHref } from "./routes";

/** A code people's choices can be filed under: letters, digits and underscores, in capitals. */
export const toCode = (text: string) =>
  text
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");

export const parseCategories = (text: string) => Array.from(new Set(text.split(",").map(toCode).filter(Boolean)));

/** Why the request can't be sent yet, or null when it can. */
export function requestProblem(req: NewRequest): string | null {
  if (!req.prpsCode) return "Say what the data is for.";
  if (req.dCatCodes.length === 0) return "Name at least one kind of data you need.";
  if (!req.ntcTitle.trim()) return "Give the notice a title.";
  if (!req.ntcText.trim()) return "Write the notice: what you'll do with the data, and that they can withdraw.";
  if (!req.extDpRefVal.trim()) return "Say who you're asking: their email address or mobile number.";
  return null;
}

/**
 * Raising a consent request: who, for what purpose, which data, and the notice the person reads before deciding.
 * The preview shows the request as the person will see it.
 */
export default function Raise({ console: c, prpsCode }: { console: Console; prpsCode?: string }) {
  const [refTyp, setRefTyp] = useState<ExtDpRefTyp>("EMAIL");
  const [refVal, setRefVal] = useState("");
  const [purpose, setPurpose] = useState(prpsCode ? humanise(prpsCode) : "");
  const [categories, setCategories] = useState("");
  const [ntcTitle, setNtcTitle] = useState("");
  const [ntcText, setNtcText] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const req: NewRequest = {
    extDpRefTyp: refTyp,
    extDpRefVal: refVal.trim(),
    prpsCode: toCode(purpose),
    dCatCodes: parseCategories(categories),
    ntcTitle: ntcTitle.trim(),
    ntcText: ntcText.trim(),
  };
  const known = c.purposes.map((p) => p.code);

  return (
    <>
      <PageHeader title="Raise a request" subtitle="Ask a person for consent. Nothing is shared until they give it." />
      {error ? <ErrorBanner message={error} /> : null}
      <div className="df-raise">
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            const problem = requestProblem(req);
            if (problem) {
              setError(problem);
              return;
            }
            setBusy(true);
            setError(null);
            const failure = await c.raise(req);
            setBusy(false);
            if (failure) setError(failure);
            else window.location.hash = purposeHref(req.prpsCode);
          }}
        >
          <Card>
            <h3 className="cm-section-title" style={{ marginTop: 0 }}>
              Who you're asking
            </h3>
            <div className="cm-form-grid">
              <Field label="Contact by">
                <select value={refTyp} onChange={(e) => setRefTyp(e.target.value as ExtDpRefTyp)}>
                  <option value="EMAIL">Email</option>
                  <option value="PHONE">Mobile number</option>
                </select>
              </Field>
              <Field label={refTyp === "EMAIL" ? "Their email address" : "Their mobile number"}>
                <input
                  type={refTyp === "EMAIL" ? "email" : "tel"}
                  value={refVal}
                  onChange={(e) => setRefVal(e.target.value)}
                  placeholder={refTyp === "EMAIL" ? "name@example.com" : "+91 98765 43210"}
                />
              </Field>
            </div>

            <h3 className="cm-section-title">What you're asking for</h3>
            <div className="cm-form-grid">
              <Field label="Purpose">
                <input list="df-purposes" value={purpose} onChange={(e) => setPurpose(e.target.value)} placeholder="e.g. Order updates" />
              </Field>
              <Field label="Data you need (comma-separated)">
                <input value={categories} onChange={(e) => setCategories(e.target.value)} placeholder="e.g. Email, Name" />
              </Field>
            </div>
            <datalist id="df-purposes">
              {known.map((k) => (
                <option key={k} value={humanise(k)} />
              ))}
            </datalist>

            <h3 className="cm-section-title">The notice they'll read</h3>
            <Field label="Title">
              <input maxLength={255} value={ntcTitle} onChange={(e) => setNtcTitle(e.target.value)} placeholder="e.g. Order updates by email" />
            </Field>
            <Field label="Notice">
              <textarea
                rows={5}
                value={ntcText}
                onChange={(e) => setNtcText(e.target.value)}
                placeholder="What you'll do with the data, for how long, who else sees it, and that they can withdraw at any time."
              />
            </Field>
            <Button type="submit" variant="primary" disabled={busy}>
              {busy ? "Sending…" : "Send request"}
            </Button>
          </Card>
        </form>

        <aside aria-label="Preview">
          <div className="cm-note" style={{ marginBottom: 6 }}>
            What the person will see
          </div>
          <div className="df-preview">
            <div className="df-preview-head">
              <strong>{c.dfName}</strong>
              <span className="cm-badge cm-badge-pending">Awaiting your decision</span>
            </div>
            <div className="df-preview-purpose">{purpose.trim() ? humanise(req.prpsCode) : "Purpose"}</div>
            <div className="df-chips">
              {(req.dCatCodes.length ? req.dCatCodes : ["DATA"]).map((d) => (
                <span key={d} className="df-chip">
                  {humanise(d)}
                </span>
              ))}
            </div>
            <div className="df-preview-notice">
              <strong>{req.ntcTitle || "Notice title"}</strong>
              <p>{req.ntcText || "Your notice appears here, exactly as you write it."}</p>
            </div>
            <div className="df-preview-actions">
              <span className="cm-btn cm-btn-primary">Give consent</span>
              <span className="cm-btn">Decline</span>
            </div>
          </div>
        </aside>
      </div>
    </>
  );
}
