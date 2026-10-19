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
import {
  ApiError,
  prps,
  type PrpsChange,
  type PrpsContent,
  type PrpsDCat,
  type PrpsDefDto,
  type PrpsFlag,
  type PrpsSensitivity,
  type PrpsVer,
} from "api-client";
import { Button, Card, ErrorBanner, Field, OrgLogo, PageHeader, Spinner, friendlyError, humanise } from "ui-kit/src/index";
import type { Console } from "./console";
import { fmtDate, grantRate } from "./labels";
import { Counts, RaiseButton, RequestTable } from "./pages";
import { managesPurposes } from "./roles";
import { purposeEditHref, purposeHref } from "./routes";
import { toCode } from "./Template";
import { freshToken } from "./token";

export const SENSITIVITY_LABELS: Record<PrpsSensitivity, string> = {
  ORDINARY: "Ordinary",
  SENSITIVE: "Sensitive",
  PHI: "Health information",
  CHILD: "A child's data",
};

export const FLAG_LABELS: Record<PrpsFlag, string> = {
  TRACKING: "Tracking",
  BEHAVIOURAL_MONITORING: "Behavioural monitoring",
  TARGETED_ADVERTISING: "Targeted advertising",
};

const CHANGE_LABELS: Record<PrpsChange["kind"], string> = {
  INITIAL: "First version",
  MATERIAL: "Needs re-consent",
  NON_MATERIAL: "Consent still holds",
};

/** A blank purpose, with the validity every consent had before purposes were versioned. */
export function emptyContent(): PrpsContent {
  return {
    name: "",
    description: "",
    dCats: [{ code: "", dpv: "", sensitivity: "ORDINARY", necessary: false, consequence: "" }],
    recipients: [],
    transfers: [],
    retentionDays: 365,
    requestedValidityDays: 365,
    maxValidityDays: 365,
    flags: [],
    notices: [{ lang: "en", title: "", text: "" }],
  };
}

/** One entry per line, blank lines dropped. */
export const linesOf = (text: string) =>
  text
    .split("\n")
    .map((l) => l.trim())
    .filter(Boolean);

/** Trimmed, with empty optional fields as null: what is sent is what the platform checks. */
export function tidy(content: PrpsContent): PrpsContent {
  const blank = (s?: string | null) => (s && s.trim() ? s.trim() : null);
  return {
    ...content,
    name: content.name.trim(),
    description: blank(content.description),
    dCats: content.dCats
      .filter((d) => d.code.trim())
      .map((d) => ({ ...d, code: toCode(d.code), dpv: blank(d.dpv), consequence: blank(d.consequence) })),
    notices: content.notices
      .filter((n) => n.title.trim() || n.text.trim())
      .map((n) => ({ lang: n.lang.trim(), title: n.title.trim(), text: n.text.trim() })),
  };
}

/** The first thing to fix before saving, or null. The platform checks everything again. */
export function contentProblem(c: PrpsContent): string | null {
  if (!c.name) return "Give the purpose a name.";
  if (c.dCats.length === 0) return "List at least one kind of data.";
  const codes = c.dCats.map((d) => d.code);
  if (new Set(codes).size !== codes.length) return "A kind of data is listed twice.";
  const unexplained = c.dCats.find((d) => d.necessary && !d.consequence);
  if (unexplained) return `Say what happens if someone refuses ${humanise(unexplained.code)}: it is marked needed.`;
  if (c.maxValidityDays < 1) return "The longest validity is at least a day.";
  if (c.requestedValidityDays < 1 || c.requestedValidityDays > c.maxValidityDays)
    return "The validity you ask for is at least a day and no more than the longest.";
  if (c.retentionDays < 1) return "Say how long you keep the data: at least a day.";
  if (c.notices.length === 0) return "Write the notice people read before deciding.";
  const incomplete = c.notices.find((n) => !n.lang || !n.title || !n.text);
  if (incomplete) return "Each notice needs a language, a title and its text.";
  return null;
}

/** Every purpose, defined or drafted, with its current version. */
export function Purposes({ console: c }: { console: Console }) {
  if (!c.defs) return <Spinner label="Loading purposes…" />;
  return (
    <>
      <PageHeader
        title="Purposes"
        subtitle="What you ask consent for. A published version never changes; changes go into the next version."
      />
      <div className="cm-toolbar">
        <span style={{ marginRight: "auto" }} />
        <Button onClick={c.reloadDefs}>Refresh</Button>
        {managesPurposes(c.role) ? (
          <a className="cm-btn cm-btn-primary" href={purposeEditHref()} style={{ textDecoration: "none" }}>
            New purpose
          </a>
        ) : null}
      </div>
      {c.defs.length === 0 ? (
        <Card>
          <strong>No purposes yet.</strong>
          <p className="cm-note">
            A purpose says what you need data for, which data, how long you keep it, who else receives it, and the
            notice people read. Requests are made from its published version.
          </p>
        </Card>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Purpose</th>
                <th>Current version</th>
                <th>Data</th>
                <th>Validity</th>
                <th>Draft</th>
              </tr>
            </thead>
            <tbody>
              {c.defs.map((d) => {
                const shown = d.current ?? d.draft;
                return (
                  <tr key={d.code}>
                    <td>
                      <a href={purposeHref(d.code)}>{shown?.name ?? d.code}</a>
                      <div className="cm-note">
                        <code>{d.code}</code>
                      </div>
                    </td>
                    <td>{d.currentVer ? `Version ${d.currentVer}` : "Not published"}</td>
                    <td>{shown ? shown.dCats.map((x) => humanise(x.code)).join(", ") : "—"}</td>
                    <td>{d.current ? `${d.current.requestedValidityDays} days (up to ${d.current.maxValidityDays})` : "—"}</td>
                    <td>{d.draftVer ? <span className="cm-badge cm-badge-pending">Version {d.draftVer}</span> : "—"}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

/** How a draft differs from the current version, and what publishing it means for people's consent. */
function ChangeSummary({ change }: { change: PrpsChange }) {
  if (change.kind === "INITIAL") {
    return <p className="cm-note">This is the purpose's first version. Once published, requests can be made from it.</p>;
  }
  return (
    <div className={change.kind === "MATERIAL" ? "df-callout" : undefined} role="status">
      <div>
        <strong>{CHANGE_LABELS[change.kind]}.</strong>{" "}
        {change.kind === "MATERIAL"
          ? "People who consented keep the scope they agreed to. To use the new one, ask them again: it is a new request."
          : "People's consent carries on; they are told what changed."}
        <ul className="df-reasons">
          {change.material.map((r) => (
            <li key={`m-${r}`}>
              <strong>{r}</strong>
            </li>
          ))}
          {change.minor.map((r) => (
            <li key={`n-${r}`}>{r}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/** What a version says, as people and auditors read it. */
function VersionContent({ content }: { content: PrpsContent }) {
  return (
    <>
      {content.description ? <p>{content.description}</p> : null}
      <div style={{ overflowX: "auto" }}>
        <table className="cm-table">
          <thead>
            <tr>
              <th>Data</th>
              <th>Type (DPV)</th>
              <th>Sensitivity</th>
              <th>If someone refuses it</th>
            </tr>
          </thead>
          <tbody>
            {content.dCats.map((d) => (
              <tr key={d.code}>
                <td>
                  {humanise(d.code)} <code className="cm-note">{d.code}</code>
                </td>
                <td>{d.dpv ?? "—"}</td>
                <td>{SENSITIVITY_LABELS[d.sensitivity]}</td>
                <td>{d.necessary ? d.consequence : (d.consequence ?? "Optional")}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <dl className="cm-fields" style={{ marginTop: 14 }}>
        <div>
          <dt>Consent lasts</dt>
          <dd>
            {content.requestedValidityDays} days asked; people may choose up to {content.maxValidityDays}
          </dd>
        </div>
        <div>
          <dt>Data kept for</dt>
          <dd>{content.retentionDays} days</dd>
        </div>
        <div>
          <dt>Recipients</dt>
          <dd>{content.recipients.length ? content.recipients.join(", ") : "None"}</dd>
        </div>
        <div>
          <dt>Transfers</dt>
          <dd>{content.transfers.length ? content.transfers.join(", ") : "None"}</dd>
        </div>
        {content.flags.length ? (
          <div>
            <dt>Special processing</dt>
            <dd>{content.flags.map((f) => FLAG_LABELS[f]).join(", ")}</dd>
          </div>
        ) : null}
      </dl>
      {content.notices.map((n) => (
        <details key={n.lang} className="df-notice">
          <summary>
            Notice ({n.lang}): {n.title}
          </summary>
          <p style={{ whiteSpace: "pre-wrap" }}>{n.text}</p>
          {n.hash ? (
            <p className="cm-note" title="SHA-256 of the notice, kept with every request made from it">
              Fingerprint <code>{n.hash.slice(0, 16)}…</code>
            </p>
          ) : null}
        </details>
      ))}
    </>
  );
}

function useDef(c: Console, code: string) {
  const [def, setDef] = useState<PrpsDefDto | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(async () => {
    try {
      setDef(await prps.get(c.gwBase, await freshToken(c.auth), code));
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) setDef(null);
      else setError(friendlyError(err));
    }
  }, [c.auth, c.gwBase, code]);
  useEffect(() => {
    load();
  }, [load]);
  return { def, setDef, error, setError, load };
}

/** One purpose: its current version, any draft with what publishing it would mean, its history and its requests. */
export function PurposeDetail({ console: c, prpsCode }: { console: Console; prpsCode: string }) {
  const { def, setDef, error, setError } = useDef(c, prpsCode);
  const [change, setChange] = useState<PrpsChange | null>(null);
  const [busy, setBusy] = useState(false);
  const draftVer = def?.draftVer;

  useEffect(() => {
    setChange(null);
    if (!draftVer) return;
    freshToken(c.auth)
      .then((token) => prps.draftChange(c.gwBase, token, prpsCode))
      .then(setChange)
      .catch((err) => setError(friendlyError(err)));
  }, [c.auth, c.gwBase, prpsCode, draftVer, setError]);

  const act = async (action: "publish" | "discard") => {
    if (action === "discard" && !window.confirm("Discard this draft? The current version stays as it is.")) return;
    setBusy(true);
    setError(null);
    try {
      const token = await freshToken(c.auth);
      setDef(action === "publish" ? await prps.publish(c.gwBase, token, prpsCode) : await prps.discardDraft(c.gwBase, token, prpsCode));
      await c.reloadDefs();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  if (def === undefined || !c.rows) return <Spinner />;
  const rows = c.rows.filter((r) => r.prpsCode === prpsCode).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const current: PrpsVer | undefined = def?.versions.find((v) => v.verNo === def.currentVer);
  const draft: PrpsVer | undefined = def?.versions.find((v) => v.sts === "DRAFT");
  const name = (current ?? draft)?.content.name ?? humanise(prpsCode);
  const rate = grantRate(rows);
  const manages = managesPurposes(c.role);

  return (
    <>
      <a className="cm-back" href="#/purposes">
        ← Purposes
      </a>
      {error ? <ErrorBanner message={error} /> : null}
      <div className="cm-head">
        <OrgLogo id={`prps:${prpsCode}`} name={name} load={async () => null} size={72} />
        <div style={{ minWidth: 0 }}>
          <h1>{name}</h1>
          <p>
            <code>{prpsCode}</code>
            {current ? ` · version ${current.verNo}, published ${fmtDate(current.publishedAt)}` : " · not published"}
            {rate !== null ? ` · ${rate}% of people who decided granted consent` : ""}
          </p>
        </div>
        <div style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
          {manages && def && !draft ? (
            <a className="cm-btn" href={purposeEditHref(prpsCode)} style={{ textDecoration: "none" }}>
              Change it
            </a>
          ) : null}
          {current ? <RaiseButton console={c} prpsCode={prpsCode} /> : null}
        </div>
      </div>

      {def === null ? (
        <p className="cm-note">
          This purpose was used by requests but isn't defined here. Requests can only be made from a defined, published
          purpose.
        </p>
      ) : null}

      {draft ? (
        <Card style={{ marginBottom: 18 }}>
          <h3 className="cm-section-title" style={{ marginTop: 0 }}>
            Draft of version {draft.verNo}
          </h3>
          {change ? <ChangeSummary change={change} /> : <Spinner label="Comparing with the current version…" />}
          {manages ? (
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginTop: 12 }}>
              <Button variant="primary" disabled={busy || !change} onClick={() => act("publish")}>
                Publish version {draft.verNo}
              </Button>
              <a className="cm-btn" href={purposeEditHref(prpsCode)} style={{ textDecoration: "none" }}>
                Edit the draft
              </a>
              <Button disabled={busy} onClick={() => act("discard")}>
                Discard
              </Button>
            </div>
          ) : null}
          <details className="df-notice" style={{ marginTop: 12 }}>
            <summary>What the draft says</summary>
            <VersionContent content={draft.content} />
          </details>
        </Card>
      ) : null}

      {current ? (
        <>
          <h3 className="cm-section-title">Version {current.verNo}: what people are asked</h3>
          <VersionContent content={current.content} />
        </>
      ) : null}

      {def && def.versions.some((v) => v.sts === "PUBLISHED") ? (
        <>
          <h3 className="cm-section-title">Versions</h3>
          <div style={{ overflowX: "auto" }}>
            <table className="cm-table">
              <thead>
                <tr>
                  <th>Version</th>
                  <th>Published</th>
                  <th>Change</th>
                  <th>What changed</th>
                </tr>
              </thead>
              <tbody>
                {def.versions
                  .filter((v) => v.sts === "PUBLISHED")
                  .reverse()
                  .map((v) => (
                    <tr key={v.id}>
                      <td>{v.verNo}</td>
                      <td>{fmtDate(v.publishedAt)}</td>
                      <td>{v.change ? CHANGE_LABELS[v.change.kind] : "—"}</td>
                      <td>{v.change ? [...v.change.material, ...v.change.minor].join("; ") || "—" : "—"}</td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}

      <h3 className="cm-section-title">Requests</h3>
      <Counts rows={rows} />
      <RequestTable rows={rows} />
    </>
  );
}

const SENSITIVITIES = Object.keys(SENSITIVITY_LABELS) as PrpsSensitivity[];
const FLAGS = Object.keys(FLAG_LABELS) as PrpsFlag[];

/**
 * Defining a purpose, or drafting its next version. Saving keeps a draft; nothing changes for anyone until it is
 * published from the purpose's page, which says first whether people must be asked again.
 */
export function PurposeEditor({ console: c, prpsCode }: { console: Console; prpsCode?: string }) {
  const [code, setCode] = useState(prpsCode ?? "");
  const [content, setContent] = useState<PrpsContent | null>(prpsCode ? null : emptyContent());
  const [recipients, setRecipients] = useState("");
  const [transfers, setTransfers] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!prpsCode) return;
    freshToken(c.auth)
      .then((token) => prps.get(c.gwBase, token, prpsCode))
      .then((def) => {
        // edit the draft if there is one, otherwise start from the current version
        const from = def.versions.find((v) => v.sts === "DRAFT") ?? def.versions.find((v) => v.verNo === def.currentVer);
        const start = from ? from.content : emptyContent();
        setContent({ ...start, notices: start.notices.map(({ lang, title, text }) => ({ lang, title, text })) });
        setRecipients(start.recipients.join("\n"));
        setTransfers(start.transfers.join("\n"));
      })
      .catch((err) => setError(friendlyError(err)));
  }, [c.auth, c.gwBase, prpsCode]);

  if (!managesPurposes(c.role)) return <p className="cm-note">Your role doesn't manage purposes.</p>;
  if (!content) return error ? <ErrorBanner message={error} /> : <Spinner />;

  const set = (patch: Partial<PrpsContent>) => setContent({ ...content, ...patch });
  const setDCat = (i: number, patch: Partial<PrpsDCat>) =>
    set({ dCats: content.dCats.map((d, j) => (j === i ? { ...d, ...patch } : d)) });
  const setNotice = (i: number, patch: Partial<{ lang: string; title: string; text: string }>) =>
    set({ notices: content.notices.map((n, j) => (j === i ? { ...n, ...patch } : n)) });
  const days = (v: string) => Math.max(0, Math.floor(Number(v) || 0));

  const save = async () => {
    const out = tidy({ ...content, recipients: linesOf(recipients), transfers: linesOf(transfers) });
    const finalCode = prpsCode ?? toCode(code || out.name);
    const problem = (finalCode ? null : "Give the purpose a code.") ?? contentProblem(out);
    if (problem) {
      setError(problem);
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const token = await freshToken(c.auth);
      if (prpsCode) await prps.saveDraft(c.gwBase, token, prpsCode, out);
      else await prps.create(c.gwBase, token, finalCode, out);
      await c.reloadDefs();
      window.location.hash = purposeHref(finalCode);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <a className="cm-back" href={prpsCode ? purposeHref(prpsCode) : "#/purposes"}>
        ← {prpsCode ? humanise(prpsCode) : "Purposes"}
      </a>
      <PageHeader
        title={prpsCode ? `Next version of ${content.name || humanise(prpsCode)}` : "New purpose"}
        subtitle="Saved as a draft. Nothing changes for anyone until you publish it."
      />
      {error ? <ErrorBanner message={error} /> : null}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
      >
        <Card>
          <h3 className="cm-section-title" style={{ marginTop: 0 }}>
            The purpose
          </h3>
          <div className="cm-form-grid">
            <Field label="Name">
              <input maxLength={120} value={content.name} onChange={(e) => set({ name: e.target.value })} placeholder="e.g. Order updates" />
            </Field>
            <Field label="Code (used by your systems; can't change later)">
              <input
                value={prpsCode ?? code}
                disabled={!!prpsCode}
                onChange={(e) => setCode(e.target.value)}
                placeholder={toCode(content.name) || "ORDER_UPDATES"}
              />
            </Field>
          </div>
          <Field label="What you do with the data">
            <textarea rows={2} maxLength={2000} value={content.description ?? ""} onChange={(e) => set({ description: e.target.value })} />
          </Field>

          <h3 className="cm-section-title">The data</h3>
          <p className="cm-note">
            Mark data as needed only if you can't go ahead without it, and say what happens then. People can still refuse
            or withdraw it.
          </p>
          {content.dCats.map((d, i) => (
            <div key={i} className="df-dcat-row">
              <Field label="Data">
                <input value={d.code} onChange={(e) => setDCat(i, { code: e.target.value })} placeholder="e.g. Email" />
              </Field>
              <Field label="DPV type (optional)">
                <input value={d.dpv ?? ""} onChange={(e) => setDCat(i, { dpv: e.target.value })} placeholder="dpv-pd:EmailAddress" />
              </Field>
              <Field label="Sensitivity">
                <select value={d.sensitivity} onChange={(e) => setDCat(i, { sensitivity: e.target.value as PrpsSensitivity })}>
                  {SENSITIVITIES.map((s) => (
                    <option key={s} value={s}>
                      {SENSITIVITY_LABELS[s]}
                    </option>
                  ))}
                </select>
              </Field>
              <label className="df-check">
                <input type="checkbox" checked={d.necessary} onChange={(e) => setDCat(i, { necessary: e.target.checked })} /> Needed
              </label>
              {d.necessary ? (
                <div className="df-dcat-wide">
                  <Field label="If someone refuses it">
                    <input
                      maxLength={1000}
                      value={d.consequence ?? ""}
                      onChange={(e) => setDCat(i, { consequence: e.target.value })}
                      placeholder="e.g. We can't send you order updates."
                    />
                  </Field>
                </div>
              ) : null}
              <Button
                type="button"
                disabled={content.dCats.length === 1}
                onClick={() => set({ dCats: content.dCats.filter((_, j) => j !== i) })}
              >
                Remove
              </Button>
            </div>
          ))}
          <Button
            type="button"
            onClick={() =>
              set({ dCats: [...content.dCats, { code: "", dpv: "", sensitivity: "ORDINARY", necessary: false, consequence: "" }] })
            }
          >
            Add data
          </Button>

          <h3 className="cm-section-title">How long, and who else</h3>
          <div className="cm-form-grid">
            <Field label="Consent asked for (days)">
              <input type="number" min={1} value={content.requestedValidityDays} onChange={(e) => set({ requestedValidityDays: days(e.target.value) })} />
            </Field>
            <Field label="Longest people may choose (days)">
              <input type="number" min={1} max={3650} value={content.maxValidityDays} onChange={(e) => set({ maxValidityDays: days(e.target.value) })} />
            </Field>
            <Field label="Data kept for (days)">
              <input type="number" min={1} value={content.retentionDays} onChange={(e) => set({ retentionDays: days(e.target.value) })} />
            </Field>
          </div>
          <div className="cm-form-grid">
            <Field label="Recipients (one per line)">
              <textarea rows={3} value={recipients} onChange={(e) => setRecipients(e.target.value)} placeholder="e.g. Courier Ltd" />
            </Field>
            <Field label="Transfers outside the country (one per line)">
              <textarea rows={3} value={transfers} onChange={(e) => setTransfers(e.target.value)} placeholder="e.g. United States (standard clauses)" />
            </Field>
          </div>
          <fieldset className="df-dcats">
            <legend>Special processing</legend>
            {FLAGS.map((f) => (
              <label key={f}>
                <input
                  type="checkbox"
                  checked={content.flags.includes(f)}
                  onChange={(e) => set({ flags: e.target.checked ? [...content.flags, f] : content.flags.filter((x) => x !== f) })}
                />{" "}
                {FLAG_LABELS[f]}
              </label>
            ))}
          </fieldset>

          <h3 className="cm-section-title">The notice people read</h3>
          <p className="cm-note">
            One per language. Say what you do with the data, for how long, who else sees it, what happens if they refuse,
            and that they can withdraw at any time.
          </p>
          {content.notices.map((n, i) => (
            <div key={i} className="df-notice-edit">
              <div className="cm-form-grid">
                <Field label="Language (such as en, hi or ta)">
                  <input value={n.lang} maxLength={35} onChange={(e) => setNotice(i, { lang: e.target.value })} />
                </Field>
                <Field label="Title">
                  <input maxLength={255} value={n.title} onChange={(e) => setNotice(i, { title: e.target.value })} />
                </Field>
              </div>
              <Field label="Notice">
                <textarea rows={5} value={n.text} onChange={(e) => setNotice(i, { text: e.target.value })} />
              </Field>
              {content.notices.length > 1 ? (
                <Button type="button" onClick={() => set({ notices: content.notices.filter((_, j) => j !== i) })}>Remove this language</Button>
              ) : null}
            </div>
          ))}
          <Button type="button" onClick={() => set({ notices: [...content.notices, { lang: "", title: "", text: "" }] })}>Add a language</Button>

          <div style={{ marginTop: 18 }}>
            <Button type="submit" variant="primary" disabled={busy}>
              {busy ? "Saving…" : "Save draft"}
            </Button>
          </div>
        </Card>
      </form>
    </>
  );
}
