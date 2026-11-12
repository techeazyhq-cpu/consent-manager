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
import { aiDraftingConfig, type AiDraftingTestResult, type AiDraftingUpdate, type AiDraftingViewDto, type AiProviderId, type AiProviderInfo } from "api-client";
import { Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import AiDraftingAccess from "./AiDraftingAccess";
import { freshToken, type Session } from "./session";

/** A host on the platform's own network: loopback, private, or a single-label or cluster-internal name. Mirrors the service, which decides. */
export function isOwnHost(host: string): boolean {
  const h = host.toLowerCase().replace(/^\[|\]$/g, "");
  if (h === "localhost" || h === "::1" || h.endsWith(".localhost") || h.endsWith(".internal") || h.endsWith(".local") || h.endsWith(".svc") || h.includes(".svc.") || !h.includes(".")) {
    return true;
  }
  if (/^(127\.|10\.|192\.168\.)/.test(h)) return true;
  const m = /^172\.(\d+)\./.exec(h);
  return !!m && Number(m[1]) >= 16 && Number(m[1]) <= 31;
}

/** Whether descriptions would be sent outside the platform's own infrastructure: a vendor's API always, a server only when its address is not its own. */
export function leavesPlatform(p: AiProviderInfo | undefined, endpoint: string): boolean {
  if (!p) return false;
  if (!p.endpointEditable) return true;
  try {
    return !isOwnHost(new URL(endpoint.trim()).hostname);
  } catch {
    return false; // not an address yet: the service says what is wrong with it
  }
}

const trimSlash = (s: string) => s.trim().replace(/\/+$/, "").toLowerCase();

interface Form {
  enabled: boolean;
  provider: AiProviderId;
  endpoint: string;
  model: string;
  apiKey: string;
  clearKey: boolean;
  timeoutSeconds: number;
  maxTokens: number;
  thirdPartyAck: boolean;
}

const formOf = (v: AiDraftingViewDto): Form => ({
  enabled: v.config.enabled,
  provider: v.config.provider,
  endpoint: v.config.endpoint,
  model: v.config.model,
  apiKey: "",
  clearKey: false,
  timeoutSeconds: v.config.timeoutSeconds,
  maxTokens: v.config.maxTokens,
  thirdPartyAck: v.config.thirdPartyAck,
});

/**
 * Which model drafts purposes for fiduciaries (ADR-113): Ollama or any OpenAI-compatible server the platform runs, or a vendor's API (OpenAI,
 * Anthropic, Google Gemini, DeepSeek). Platform staff can read the setting; administrators change it. The key is write-only: it is sent when
 * saved and never shown again. Choosing a vendor sends fiduciaries' descriptions to it, so that needs the administrator's explicit say-so.
 */
export default function AiDrafting({ session }: { session: Session }) {
  const [view, setView] = useState<AiDraftingViewDto | null>(null);
  const [form, setForm] = useState<Form | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [tested, setTested] = useState<AiDraftingTestResult | null>(null);

  const load = useCallback(async () => {
    try {
      const v = await aiDraftingConfig.get(session.gwBase, await freshToken(session.auth));
      setView(v);
      setForm(formOf(v));
    } catch (err) {
      setError(friendlyError(err));
    }
  }, [session.auth, session.gwBase]);

  useEffect(() => {
    load();
  }, [load]);

  if (!view || !form) return error ? <ErrorBanner message={error} /> : <Spinner />;

  const providers = view.providers;
  const info = providers.find((p) => p.id === form.provider);
  const canEdit = session.isAdmin;
  const outside = leavesPlatform(info, form.endpoint);
  const sameAsSaved =
    form.provider === view.config.provider && (!info?.endpointEditable || trimSlash(form.endpoint) === trimSlash(view.config.endpoint));
  const keyKept = view.config.apiKeySet && sameAsSaved && !form.clearKey;
  const set = (patch: Partial<Form>) => {
    setForm({ ...form, ...patch });
    setSaved(false);
    setTested(null);
  };

  const chooseProvider = (id: AiProviderId) => {
    const next = providers.find((p) => p.id === id);
    if (!next) return;
    set({
      provider: id,
      endpoint: next.defaultEndpoint,
      model: next.defaultModel,
      apiKey: "",
      clearKey: false,
      thirdPartyAck: false,
    });
  };

  const update = (): AiDraftingUpdate => ({
    enabled: form.enabled,
    provider: form.provider,
    endpoint: form.endpoint.trim(),
    model: form.model.trim(),
    apiKey: form.apiKey.trim() || undefined,
    clearKey: form.clearKey || undefined,
    timeoutSeconds: form.timeoutSeconds,
    maxTokens: form.maxTokens,
    thirdPartyAck: form.thirdPartyAck,
  });

  const needsKey = !!info?.keyRequired && !keyKept && !form.apiKey.trim();
  const needsAck = form.enabled && outside && !form.thirdPartyAck;
  const invalid = !form.model.trim() || (form.enabled && needsKey);

  const save = async () => {
    setBusy(true);
    setError(null);
    setTested(null);
    try {
      const v = await aiDraftingConfig.save(session.gwBase, await freshToken(session.auth), update());
      setView(v);
      setForm(formOf(v));
      setSaved(true);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const test = async () => {
    setBusy(true);
    setError(null);
    setTested(null);
    try {
      setTested(await aiDraftingConfig.test(session.gwBase, await freshToken(session.auth), update()));
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="AI drafting model"
        subtitle="Which model helps fiduciaries draft a purpose from a description. It only ever suggests: a person reads, edits and saves every draft."
      />
      {error ? <ErrorBanner message={error} /> : null}
      {!canEdit ? <p className="cm-note">You can see this setting; only a platform administrator changes it.</p> : null}
      {view.fromProperties ? (
        <p className="cm-note">
          Nothing has been saved here yet, so the service&apos;s own configuration is in use. Saving this page replaces it.
        </p>
      ) : null}
      <Card style={{ marginBottom: 16 }}>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (canEdit && !invalid && !needsAck) save();
          }}
        >
          <fieldset disabled={!canEdit || busy} style={{ border: 0, padding: 0, margin: 0 }}>
            <label style={{ display: "block", marginBottom: 12 }}>
              <input type="checkbox" checked={form.enabled} onChange={(e) => set({ enabled: e.target.checked })} /> Offer drafting with a model in
              the DF Console
            </label>
            <div className="cm-form-grid">
              <Field label="Provider">
                <select value={form.provider} onChange={(e) => chooseProvider(e.target.value as AiProviderId)}>
                  {providers.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.label}
                      {p.thirdParty ? " (vendor's API)" : " (you run it)"}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Model">
                <input value={form.model} maxLength={120} placeholder={info?.defaultModel} onChange={(e) => set({ model: e.target.value })} />
              </Field>
              {info?.endpointEditable ? (
                <Field label="Server address">
                  <input value={form.endpoint} placeholder={info.defaultEndpoint} onChange={(e) => set({ endpoint: e.target.value })} />
                </Field>
              ) : (
                <Field label="Server address">
                  <input value={info?.defaultEndpoint ?? ""} readOnly aria-readonly="true" />
                </Field>
              )}
              <Field label={info?.keyRequired ? "API key" : "API key (only if the server wants one)"}>
                <input
                  type="password"
                  autoComplete="off"
                  value={form.apiKey}
                  placeholder={keyKept ? "A key is stored: leave blank to keep it" : ""}
                  onChange={(e) => set({ apiKey: e.target.value, clearKey: false })}
                />
              </Field>
              <Field label="Wait for the model (seconds)">
                <input type="number" min={5} max={120} value={form.timeoutSeconds} onChange={(e) => set({ timeoutSeconds: Number(e.target.value) })} />
              </Field>
              <Field label="Longest reply (tokens)">
                <input type="number" min={256} max={8000} value={form.maxTokens} onChange={(e) => set({ maxTokens: Number(e.target.value) })} />
              </Field>
            </div>
            {view.config.apiKeySet && sameAsSaved ? (
              <label style={{ display: "block", margin: "8px 0" }}>
                <input type="checkbox" checked={form.clearKey} onChange={(e) => set({ clearKey: e.target.checked, apiKey: "" })} /> Remove the stored key
              </label>
            ) : null}
            {form.enabled && outside ? (
              <div role="note" className="cm-note" style={{ margin: "12px 0" }}>
                <strong>This sends fiduciaries&apos; descriptions outside the platform.</strong> Each description (screened first for emails, phone
                numbers, PAN, Aadhaar-shaped and card numbers) goes to {info?.endpointEditable ? "the server above" : info?.label}, under that
                party&apos;s own terms. It receives no consent, principal or record.
                <label style={{ display: "block", marginTop: 8 }}>
                  <input type="checkbox" checked={form.thirdPartyAck} onChange={(e) => set({ thirdPartyAck: e.target.checked })} /> I choose to send
                  them there, and this is recorded in the audit trail.
                </label>
              </div>
            ) : null}
            {canEdit ? (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                <Button type="submit" variant="primary" disabled={busy || invalid || needsAck}>
                  Save
                </Button>
                <Button type="button" disabled={busy || !form.model.trim() || needsKey} onClick={test}>
                  Test the connection
                </Button>
                {saved ? <span role="status">Saved. It is in use now.</span> : null}
              </div>
            ) : null}
          </fieldset>
          {tested ? (
            <p role="status" className={tested.ok ? "cm-note" : "cm-error"} style={{ marginTop: 12 }}>
              {tested.ok ? "Working" : "Not working"}: {tested.message} ({tested.millis} ms) A test sends one line, never anyone&apos;s description.
            </p>
          ) : null}
        </form>
      </Card>
      <AiDraftingAccess session={session} />
      {view.config.updatedBy && view.config.updatedAt ? (
        <p className="cm-note">
          Last changed by <code>{view.config.updatedBy}</code> on {new Date(view.config.updatedAt).toLocaleString()}.
        </p>
      ) : null}
    </>
  );
}
