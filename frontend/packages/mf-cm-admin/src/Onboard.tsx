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
import { bil, dfReg, type DfRegisterReqDto, type SubPln } from "api-client";
import { Button, Card, ErrorBanner, Field, PageHeader, friendlyError } from "ui-kit/src/index";
import { forgetLogo } from "./DfLogo";
import { tenantHref } from "./routes";
import { rememberNotice, rememberSecret } from "./secrets";
import { freshToken, type Session } from "./session";

const LOGO_TYPES = ["image/png", "image/jpeg"];
const LOGO_MAX_BYTES = 256 * 1024;

const EMPTY: DfRegisterReqDto = {
  name: "",
  contactEmail: "",
  subPlnName: "FREE",
  legalName: "",
  contactPhone: "",
  website: "",
  registeredAddress: "",
  industry: "",
  cin: "",
  gstin: "",
  dpoName: "",
  dpoEmail: "",
};

/** The checks the server makes too, so most mistakes are caught before anything is sent. */
export function logoProblem(file: File): string | null {
  if (!LOGO_TYPES.includes(file.type)) return "The logo must be a PNG or JPEG image.";
  if (file.size > LOGO_MAX_BYTES) return "The logo must be at most 256 KB.";
  return null;
}

/** Onboards a data fiduciary: registers it, adds its logo, and optionally activates it straight away. */
export default function Onboard({ session }: { session: Session }) {
  const [form, setForm] = useState<DfRegisterReqDto>(EMPTY);
  const [plans, setPlans] = useState<SubPln[]>([]);
  const [logo, setLogo] = useState<File | null>(null);
  const [logoPreview, setLogoPreview] = useState<string | null>(null);
  const [activateNow, setActivateNow] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    freshToken(session.auth)
      .then((token) => bil.listSubPlns(session.gwBase, token))
      .then(setPlans)
      .catch((err) => setError(friendlyError(err)));
  }, [session]);

  useEffect(() => () => {
    if (logoPreview) URL.revokeObjectURL(logoPreview);
  }, [logoPreview]);

  const set = (key: keyof DfRegisterReqDto) => (e: { target: { value: string } }) =>
    setForm({ ...form, [key]: e.target.value });

  const submit = async () => {
    setBusy(true);
    setError(null);
    let dfId: string | null = null;
    try {
      const token = await freshToken(session.auth);
      // blank optional fields are left out rather than sent as empty strings
      const body = Object.fromEntries(
        Object.entries(form).map(([k, v]) => [k, typeof v === "string" ? v.trim() : v]).filter(([, v]) => v !== ""),
      ) as unknown as DfRegisterReqDto;
      const created = await dfReg.register(session.gwBase, token, body);
      dfId = created.id;
      if (logo) {
        await dfReg.uploadLogo(session.gwBase, token, created.id, logo);
        forgetLogo(created.id);
      }
      if (activateNow) {
        const active = await dfReg.activate(session.gwBase, await freshToken(session.auth), created.id);
        if (active.cmDfApiClientSecret) rememberSecret(created.id, active.cmDfApiClientSecret);
      }
      window.location.hash = tenantHref(created.id);
    } catch (err) {
      // a failure after registering leaves the tenant in place: open it, saying what is left to do there
      if (dfId) {
        rememberNotice(dfId, `The tenant was registered, but a later step failed: ${friendlyError(err)}`);
        window.location.hash = tenantHref(dfId);
        return;
      }
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Onboard tenant"
        subtitle="Register a data fiduciary. Activating it creates its API client and invites the contact as its Owner."
      />
      {error ? <ErrorBanner message={error} /> : null}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <Card>
          <h3 className="adm-section-title" style={{ marginTop: 0 }}>
            Organisation
          </h3>
          <div className="adm-form-grid">
            <Field label="Display name *">
              <input required maxLength={200} value={form.name} onChange={set("name")} />
            </Field>
            <Field label="Contact email * (becomes the Owner)">
              <input required type="email" maxLength={255} value={form.contactEmail} onChange={set("contactEmail")} />
            </Field>
            <Field label="Plan">
              <select value={form.subPlnName} onChange={set("subPlnName")}>
                {plans.length === 0 ? <option value="FREE">FREE</option> : null}
                {plans.map((p) => (
                  <option key={p.id} value={p.name}>
                    {p.name} — {p.reqPerMonthQta.toLocaleString()} API calls a month
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Logo (PNG or JPEG, up to 256 KB)">
              <input
                type="file"
                accept="image/png,image/jpeg"
                onChange={(e) => {
                  const file = e.target.files?.[0] ?? null;
                  if (logoPreview) URL.revokeObjectURL(logoPreview);
                  const problem = file ? logoProblem(file) : null;
                  setError(problem);
                  setLogo(problem ? null : file);
                  setLogoPreview(file && !problem ? URL.createObjectURL(file) : null);
                }}
              />
            </Field>
          </div>
          {logoPreview ? (
            <div className="adm-logo" style={{ width: 72, height: 72, marginBottom: 12 }}>
              <img src={logoPreview} alt="Logo preview" />
            </div>
          ) : null}

          <h3 className="adm-section-title">Legal details</h3>
          <div className="adm-form-grid">
            <Field label="Legal name">
              <input maxLength={200} value={form.legalName} onChange={set("legalName")} />
            </Field>
            <Field label="Industry">
              <input maxLength={100} value={form.industry} onChange={set("industry")} placeholder="e.g. Healthcare" />
            </Field>
            <Field label="Phone">
              <input type="tel" value={form.contactPhone} onChange={set("contactPhone")} placeholder="+91 98765 43210" />
            </Field>
            <Field label="Website">
              <input type="url" value={form.website} onChange={set("website")} placeholder="https://" />
            </Field>
            <Field label="Registered address">
              <input maxLength={500} value={form.registeredAddress} onChange={set("registeredAddress")} />
            </Field>
            <Field label="CIN">
              <input value={form.cin} onChange={set("cin")} />
            </Field>
            <Field label="GSTIN">
              <input value={form.gstin} onChange={set("gstin")} />
            </Field>
          </div>

          <h3 className="adm-section-title">Data Protection Officer</h3>
          <div className="adm-form-grid">
            <Field label="Name">
              <input maxLength={100} value={form.dpoName} onChange={set("dpoName")} />
            </Field>
            <Field label="Email">
              <input type="email" value={form.dpoEmail} onChange={set("dpoEmail")} />
            </Field>
          </div>

          <label style={{ display: "flex", gap: 8, alignItems: "center", margin: "8px 0 16px" }}>
            <input type="checkbox" checked={activateNow} onChange={(e) => setActivateNow(e.target.checked)} />
            Activate now
          </label>
          <div style={{ display: "flex", gap: 8 }}>
            <Button type="submit" variant="primary" disabled={busy || !form.name.trim() || !form.contactEmail.trim()}>
              {busy ? "Onboarding…" : "Onboard tenant"}
            </Button>
            <a className="cm-btn" href="#/dashboard" style={{ textDecoration: "none" }}>
              Cancel
            </a>
          </div>
        </Card>
      </form>
    </>
  );
}
