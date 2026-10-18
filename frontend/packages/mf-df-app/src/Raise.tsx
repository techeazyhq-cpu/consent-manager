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
import { Button, Card, ErrorBanner, Field, PageHeader } from "ui-kit/src/index";
import type { Console, NewRequest } from "./console";
import { purposeHref } from "./routes";
import { RequestPreview, TemplateFields, templateProblem, useTemplateForm } from "./Template";

export { parseCategories, toCode } from "./Template";

/** Why the request can't be sent yet, or null when it can. */
export function requestProblem(req: NewRequest): string | null {
  return templateProblem(req) ?? (req.extDpRefVal.trim() ? null : "Say who you're asking: their email address or mobile number.");
}

/**
 * Raising a consent request for one person: who, for what purpose, which data, and the notice they read before
 * deciding. The preview shows the request as they will see it. For many people, see "Send to many".
 */
export default function Raise({ console: c, prpsCode }: { console: Console; prpsCode?: string }) {
  const [refTyp, setRefTyp] = useState<ExtDpRefTyp>("EMAIL");
  const [refVal, setRefVal] = useState("");
  const form = useTemplateForm(prpsCode);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const req: NewRequest = { extDpRefTyp: refTyp, extDpRefVal: refVal.trim(), ...form.template };

  return (
    <>
      <PageHeader title="Raise a request" subtitle="Ask one person for consent. Nothing is shared until they give it." />
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
              <Field label={refTyp === "EMAIL" ? "Their email address" : "Their mobile number, with country code"}>
                <input
                  type={refTyp === "EMAIL" ? "email" : "tel"}
                  value={refVal}
                  onChange={(e) => setRefVal(e.target.value)}
                  placeholder={refTyp === "EMAIL" ? "name@example.com" : "+91 98765 43210"}
                />
              </Field>
            </div>
            <p className="cm-note">
              Asking many people? <a href="#/send-to-many">Send to many</a> takes a CSV or Excel list.
            </p>
            <TemplateFields form={form} known={c.purposes.map((p) => p.code)} />
            <Button type="submit" variant="primary" disabled={busy}>
              {busy ? "Sending…" : "Send request"}
            </Button>
          </Card>
        </form>
        <RequestPreview console={c} form={form} />
      </div>
    </>
  );
}
