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
import type { CnsReqBatchTemplate, PrpsContent, PrpsNtc, PrpsSummaryDto } from "api-client";
import { Field, humanise } from "ui-kit/src/index";
import type { Console } from "./console";
import { managesPurposes } from "./roles";
import { purposeEditHref } from "./routes";

/** A code people's choices can be filed under: letters, digits and underscores, in capitals. */
export const toCode = (text: string) =>
  text
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");

/** Why the request can't be sent yet, or null when it can. */
export function templateProblem(t: CnsReqBatchTemplate): string | null {
  if (!t.prpsCode) return "Choose the purpose you're asking consent for.";
  if (!t.prpsVer) return "That purpose has no published version yet. Publish it first.";
  if (t.dCatCodes && t.dCatCodes.length === 0) return "Ask for at least one kind of data.";
  return null;
}

/** The notice in `lang`; with none chosen, English if there is one, otherwise the first written (as the platform). */
export function noticeIn(content: PrpsContent, lang?: string): PrpsNtc | undefined {
  if (lang) return content.notices.find((n) => n.lang === lang);
  return content.notices.find((n) => n.lang === "en") ?? content.notices[0];
}

/** The purposes a request can be made from: those with a published version. */
export const askable = (defs: PrpsSummaryDto[] | null) =>
  (defs ?? []).filter((d): d is PrpsSummaryDto & { current: PrpsContent; currentVer: number } => !!d.current && !!d.currentVer);

/**
 * What a request asks (ADR-031): a purpose's current version, the notice's language, and which of its data
 * categories. Every category is asked for until unticked; a request can never ask for more than the purpose covers.
 */
export function useTemplateForm(defs: PrpsSummaryDto[] | null, prpsCode?: string) {
  const [code, setCodeState] = useState(prpsCode ?? "");
  const [lang, setLang] = useState("");
  const [left, setLeft] = useState<string[]>([]);
  const def = askable(defs).find((d) => d.code === code);
  const content = def?.current;
  const notice = content ? noticeIn(content, lang || undefined) : undefined;
  const dCatCodes = content ? content.dCats.map((d) => d.code).filter((c) => !left.includes(c)) : [];
  const template: CnsReqBatchTemplate = {
    prpsCode: def ? def.code : "",
    prpsVer: def?.currentVer ?? 0,
    ntcLang: notice?.lang,
    dCatCodes,
  };
  const setCode = (next: string) => {
    setCodeState(next);
    setLang("");
    setLeft([]);
  };
  const toggle = (dCat: string, asked: boolean) =>
    setLeft((prev) => (asked ? prev.filter((c) => c !== dCat) : [...prev, dCat]));
  return { code, setCode, lang, setLang, content, notice, toggle, template };
}

export type TemplateForm = ReturnType<typeof useTemplateForm>;

/** The purpose, the notice's language and the data asked for, from the purposes published. */
export function TemplateFields({ console: c, form }: { console: Console; form: TemplateForm }) {
  const options = askable(c.defs);
  const content = form.content;
  return (
    <>
      <h3 className="cm-section-title">What you're asking for</h3>
      {c.defs && options.length === 0 ? (
        <div className="df-callout" role="status">
          <span>
            Requests are made from a published purpose: what the data is for, which data, and the notice people read.
          </span>
          {managesPurposes(c.role) ? (
            <a className="cm-btn" href={purposeEditHref()} style={{ textDecoration: "none" }}>
              Define a purpose
            </a>
          ) : null}
        </div>
      ) : null}
      <div className="cm-form-grid">
        <Field label="Purpose">
          <select value={form.code} onChange={(e) => form.setCode(e.target.value)}>
            <option value="">Choose a purpose…</option>
            {options.map((d) => (
              <option key={d.code} value={d.code}>
                {d.current.name} (version {d.currentVer})
              </option>
            ))}
          </select>
        </Field>
        {content && content.notices.length > 1 ? (
          <Field label="Notice language">
            <select value={form.notice?.lang ?? ""} onChange={(e) => form.setLang(e.target.value)}>
              {content.notices.map((n) => (
                <option key={n.lang} value={n.lang}>
                  {n.lang}
                </option>
              ))}
            </select>
          </Field>
        ) : null}
      </div>
      {content ? (
        <fieldset className="df-dcats">
          <legend>Data you're asking for</legend>
          {content.dCats.map((d) => (
            <label key={d.code}>
              <input
                type="checkbox"
                checked={form.template.dCatCodes?.includes(d.code) ?? false}
                onChange={(e) => form.toggle(d.code, e.target.checked)}
              />{" "}
              {humanise(d.code)}
              {d.necessary ? <span className="cm-note"> · needed: {d.consequence}</span> : null}
            </label>
          ))}
          <p className="cm-note">
            Consent is asked for {content.requestedValidityDays} days; people may choose up to{" "}
            {content.maxValidityDays}. To change the data, notice or validity, publish a new version of the purpose.
          </p>
        </fieldset>
      ) : null}
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
        <div className="df-preview-purpose">{t.prpsCode ? humanise(t.prpsCode) : "Purpose"}</div>
        <div className="df-chips">
          {(t.dCatCodes?.length ? t.dCatCodes : ["DATA"]).map((d) => (
            <span key={d} className="df-chip">
              {humanise(d)}
            </span>
          ))}
        </div>
        <div className="df-preview-notice">
          <strong>{form.notice?.title ?? "Notice title"}</strong>
          <p>{form.notice?.text ?? "The purpose's notice appears here, exactly as it was published."}</p>
        </div>
        <div className="df-preview-actions">
          <span className="cm-btn cm-btn-primary">Give consent</span>
          <span className="cm-btn">Decline</span>
        </div>
      </div>
    </aside>
  );
}
