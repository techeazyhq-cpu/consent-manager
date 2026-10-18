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
import type { CnsReqBatchTemplate } from "api-client";
import { Field, humanise } from "ui-kit/src/index";
import type { Console } from "./console";

/** A code people's choices can be filed under: letters, digits and underscores, in capitals. */
export const toCode = (text: string) =>
  text
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");

export const parseCategories = (text: string) => Array.from(new Set(text.split(",").map(toCode).filter(Boolean)));

/** Why the template can't be used yet, or null when it can. */
export function templateProblem(t: CnsReqBatchTemplate): string | null {
  if (!t.prpsCode) return "Say what the data is for.";
  if (t.dCatCodes.length === 0) return "Name at least one kind of data you need.";
  if (!t.ntcTitle.trim()) return "Give the notice a title.";
  if (!t.ntcText.trim()) return "Write the notice: what you'll do with the data, and that they can withdraw.";
  return null;
}

/** What is asked of a person (purpose, data, notice), as typed and as the codes sent. */
export function useTemplateForm(prpsCode?: string) {
  const [purpose, setPurpose] = useState(prpsCode ? humanise(prpsCode) : "");
  const [categories, setCategories] = useState("");
  const [ntcTitle, setNtcTitle] = useState("");
  const [ntcText, setNtcText] = useState("");
  const template: CnsReqBatchTemplate = {
    prpsCode: toCode(purpose),
    dCatCodes: parseCategories(categories),
    ntcTitle: ntcTitle.trim(),
    ntcText: ntcText.trim(),
  };
  return { purpose, setPurpose, categories, setCategories, ntcTitle, setNtcTitle, ntcText, setNtcText, template };
}

export type TemplateForm = ReturnType<typeof useTemplateForm>;

/** The purpose, data and notice fields, suggesting purposes used before. */
export function TemplateFields({ form, known }: { form: TemplateForm; known: string[] }) {
  return (
    <>
      <h3 className="cm-section-title">What you're asking for</h3>
      <div className="cm-form-grid">
        <Field label="Purpose">
          <input list="df-purposes" value={form.purpose} onChange={(e) => form.setPurpose(e.target.value)} placeholder="e.g. Order updates" />
        </Field>
        <Field label="Data you need (comma-separated)">
          <input value={form.categories} onChange={(e) => form.setCategories(e.target.value)} placeholder="e.g. Email, Name" />
        </Field>
      </div>
      <datalist id="df-purposes">
        {known.map((k) => (
          <option key={k} value={humanise(k)} />
        ))}
      </datalist>

      <h3 className="cm-section-title">The notice they'll read</h3>
      <Field label="Title">
        <input maxLength={255} value={form.ntcTitle} onChange={(e) => form.setNtcTitle(e.target.value)} placeholder="e.g. Order updates by email" />
      </Field>
      <Field label="Notice">
        <textarea
          rows={5}
          value={form.ntcText}
          onChange={(e) => form.setNtcText(e.target.value)}
          placeholder="What you'll do with the data, for how long, who else sees it, and that they can withdraw at any time."
        />
      </Field>
    </>
  );
}

/** The request exactly as a person will see it in their portal. */
export function RequestPreview({ console: c, form }: { console: Console; form: TemplateForm }) {
  const t = form.template;
  return (
    <aside aria-label="Preview">
      <div className="cm-note" style={{ marginBottom: 6 }}>
        What each person will see
      </div>
      <div className="df-preview">
        <div className="df-preview-head">
          <strong>{c.dfName}</strong>
          <span className="cm-badge cm-badge-pending">Awaiting your decision</span>
        </div>
        <div className="df-preview-purpose">{form.purpose.trim() ? humanise(t.prpsCode) : "Purpose"}</div>
        <div className="df-chips">
          {(t.dCatCodes.length ? t.dCatCodes : ["DATA"]).map((d) => (
            <span key={d} className="df-chip">
              {humanise(d)}
            </span>
          ))}
        </div>
        <div className="df-preview-notice">
          <strong>{t.ntcTitle || "Notice title"}</strong>
          <p>{t.ntcText || "Your notice appears here, exactly as you write it."}</p>
        </div>
        <div className="df-preview-actions">
          <span className="cm-btn cm-btn-primary">Give consent</span>
          <span className="cm-btn">Decline</span>
        </div>
      </div>
    </aside>
  );
}
