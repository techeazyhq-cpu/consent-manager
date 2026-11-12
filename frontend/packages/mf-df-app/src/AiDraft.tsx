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

import { useEffect, useState } from "react";
import { prps, type PrpsAiStatusDto, type PrpsAiSuggestionDto } from "api-client";
import { Button, Card, ErrorBanner, friendlyError } from "ui-kit/src/index";
import type { Console } from "./console";
import { freshToken } from "./token";

/**
 * Drafting a purpose from a description with the operator's own model (ADR-111). It only offers itself when the platform has a model set up. What comes
 * back is a suggestion for the form, never saved: it asks the person to read it, and lists what a model cannot know (the numbers, who receives the
 * data, anything it judged sensitive) as things to tick before the draft can be saved.
 */
export function AiDraftPanel({ console: c, onSuggestion }: { console: Console; onSuggestion: (s: PrpsAiSuggestionDto) => void }) {
  const [status, setStatus] = useState<PrpsAiStatusDto | null>(null);
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const [lang, setLang] = useState("en");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [reload, setReload] = useState(0);

  useEffect(() => {
    let live = true;
    freshToken(c.auth)
      .then((t) => prps.aiStatus(c.gwBase, t))
      .then((s) => live && setStatus(s))
      .catch(() => live && setStatus(null)); // not offered when it cannot be asked: the form works without it
    return () => {
      live = false;
    };
  }, [c.auth, c.gwBase, reload]);

  // the platform has no model: nothing to offer or ask for
  if (!status?.modelOffered) return null;
  // the platform has one, but staff have not turned it on for this organisation: it can ask (ADR-114)
  if (!status.enabled) return <AiAccessCard console={c} status={status} onChanged={() => setReload((n) => n + 1)} />;

  const ask = async () => {
    setBusy(true);
    setError(null);
    try {
      const s = await prps.aiDraft(c.gwBase, await freshToken(c.auth), text.trim(), lang.trim() || "en");
      onSuggestion(s);
      setOpen(false);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const tooShort = text.trim().length < status.minChars;
  return (
    <Card>
      <h3 className="cm-section-title" style={{ marginTop: 0 }}>
        Draft with AI
      </h3>
      {!open ? (
        <>
          <p className="cm-note">
            Describe the purpose in your own words and a model that runs on this platform's own servers suggests the fields and the notice. You read it,
            change it and save it as any draft; nothing is saved or published for you.
          </p>
          <Button onClick={() => setOpen(true)}>Describe the purpose</Button>
        </>
      ) : (
        <>
          <p className="cm-note">
            <strong>Do not paste anyone's details</strong>: no names, email addresses, phone numbers or identity numbers. Describe the purpose in general words.
            Obvious ones are refused, but a name in a sentence cannot always be told.
          </p>
          {error ? <ErrorBanner message={error} /> : null}
          <label className="cm-field">
            <span>What you ask people's consent for, and what you do with their data</span>
            <textarea
              rows={6}
              maxLength={status.maxChars}
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="e.g. We email customers when their order ships, using their email address and mobile number, and share the delivery address with our courier. We keep it 90 days."
            />
          </label>
          <label className="cm-field">
            <span>Language of the notice (such as en, hi, ta)</span>
            <input value={lang} maxLength={20} onChange={(e) => setLang(e.target.value)} />
          </label>
          <p className="cm-note">
            {text.trim().length} of {status.maxChars} characters. Model: {status.model}. It can take a minute.
          </p>
          <div style={{ display: "flex", gap: 8 }}>
            <Button variant="primary" disabled={busy || tooShort} onClick={ask}>
              {busy ? "Drafting…" : "Draft it"}
            </Button>
            <Button disabled={busy} onClick={() => setOpen(false)}>
              Cancel
            </Button>
          </div>
        </>
      )}
    </Card>
  );
}

/**
 * Drafting with a model is turned on per organisation by the platform's staff, at onboarding or when it asks (ADR-114). Until then this says so, lets the
 * organisation ask (with a note), shows a request that is waiting, which it can withdraw, and says why a request was turned down.
 */
export function AiAccessCard({ console: c, status, onChanged }: { console: Console; status: PrpsAiStatusDto; onChanged: () => void }) {
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (action: (token: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action(await freshToken(c.auth));
      setNote("");
      onChanged();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <h3 className="cm-section-title" style={{ marginTop: 0 }}>
        Draft with AI
      </h3>
      {error ? <ErrorBanner message={error} /> : null}
      {status.access === "PENDING" ? (
        <>
          <p role="status">
            You asked for this{status.requestedAt ? ` on ${new Date(status.requestedAt).toLocaleDateString()}` : ""}. The platform&apos;s staff have not answered yet.
          </p>
          <Button disabled={busy} onClick={() => run((t) => prps.aiWithdrawAccess(c.gwBase, t))}>
            Withdraw the request
          </Button>
        </>
      ) : (
        <>
          <p className="cm-note">
            Describe a purpose in your own words and a model suggests the fields and the notice, for you to read, change and save. It is not switched on
            for your organisation: platform staff turn it on, and you can ask.
          </p>
          {status.access === "DECLINED" ? (
            <p role="status">
              Your last request was turned down{status.declineReason ? `: ${status.declineReason}` : ""}. You can ask again.
            </p>
          ) : null}
          <label className="cm-field">
            <span>Why you would like it (optional)</span>
            <textarea rows={2} maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} />
          </label>
          <Button variant="primary" disabled={busy} onClick={() => run((t) => prps.aiRequestAccess(c.gwBase, t, note))}>
            Ask to turn it on
          </Button>
        </>
      )}
    </Card>
  );
}

/** What a person must check before an AI draft is saved: the warnings, and each thing a model cannot know, ticked one by one. */
export function AiReview({
  suggestion,
  ticked,
  onTick,
}: {
  suggestion: PrpsAiSuggestionDto;
  ticked: boolean[];
  onTick: (i: number, on: boolean) => void;
}) {
  return (
    <Card>
      <h3 className="cm-section-title" style={{ marginTop: 0 }}>
        Drafted by AI: check before you save
      </h3>
      {suggestion.notes.map((n) => (
        <p key={n} className="cm-note">
          {n}
        </p>
      ))}
      <p className="cm-note">A model cannot know these. Tick each one when you have checked it is right for you; the draft can be saved once all are ticked.</p>
      <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
        {suggestion.confirm.map((item, i) => (
          <li key={item}>
            <label>
              <input type="checkbox" checked={!!ticked[i]} onChange={(e) => onTick(i, e.target.checked)} /> {item}
            </label>
          </li>
        ))}
      </ul>
      <p className="cm-note">Model: {suggestion.model}.</p>
    </Card>
  );
}

/** True when every item a person had to confirm has been ticked (or there was nothing to confirm). */
export const allTicked = (ticked: boolean[], count: number) => Array.from({ length: count }).every((_, i) => ticked[i]);
