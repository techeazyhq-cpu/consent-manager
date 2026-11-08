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

import { useEffect, useRef, useState } from "react";
import { signup } from "api-client";
import { Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { applicationFrom, canApply, type SignupRoute } from "./signupRoute";

/**
 * The public sign-up page (ADR-065), for an organisation without an account. It asks for the organisation's name and a contact
 * address, mails a link to that address, and the link's page confirms it. Staff then look at the application: nobody can sign
 * in until they approve it.
 */
export default function Signup({ route, gwBase }: { route: SignupRoute; gwBase: string }) {
  return (
    <div className="cm-page" style={{ maxWidth: 640, margin: "0 auto", padding: "32px 16px" }}>
      {route.page === "verify" ? <Verify id={route.id} token={route.token} gwBase={gwBase} /> : <Form gwBase={gwBase} />}
    </div>
  );
}

function Form({ gwBase }: { gwBase: string }) {
  const [form, setForm] = useState({ name: "", contactEmail: "", legalName: "", industry: "", website: "" });
  const [busy, setBusy] = useState(false);
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const set = (key: keyof typeof form) => (e: { target: { value: string } }) => setForm({ ...form, [key]: e.target.value });

  if (sentTo) {
    return (
      <>
        <PageHeader title="Check your email" />
        <Card>
          <p>
            If <strong>{sentTo}</strong> can receive mail, a link to confirm it is on its way. It works once and for 24 hours.
          </p>
          <p className="cm-note">
            After you confirm it, our team looks at your application and writes to you. You can sign in to the DF Console once it is
            approved.
          </p>
        </Card>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Apply to use Consent Manager" subtitle="For organisations that ask people for consent to use their data." />
      {error ? <ErrorBanner message={error} /> : null}
      <Card>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError(null);
            try {
              await signup.apply(gwBase, applicationFrom(form));
              setSentTo(form.contactEmail.trim());
            } catch (err) {
              setError(friendlyError(err));
            } finally {
              setBusy(false);
            }
          }}
        >
          <div className="cm-form-grid">
            <Field label="Organisation name">
              <input value={form.name} maxLength={255} onChange={set("name")} required />
            </Field>
            <Field label="Contact email (we send a link to confirm it)">
              <input type="email" value={form.contactEmail} maxLength={320} onChange={set("contactEmail")} required />
            </Field>
            <Field label="Legal name (optional)">
              <input value={form.legalName} maxLength={255} onChange={set("legalName")} />
            </Field>
            <Field label="Industry (optional)">
              <input value={form.industry} maxLength={128} onChange={set("industry")} />
            </Field>
            <Field label="Website (optional)">
              <input value={form.website} maxLength={255} onChange={set("website")} placeholder="https://" />
            </Field>
          </div>
          <Button type="submit" variant="primary" disabled={busy || !canApply(form.name, form.contactEmail)}>
            {busy ? "Sending…" : "Send the confirmation link"}
          </Button>
        </form>
      </Card>
      <p className="cm-note">
        Already have an account? <a href="#/dashboard">Sign in</a>. <a href="#/status" onClick={() => setTimeout(() => window.location.reload())}>Platform status</a>.
      </p>
    </>
  );
}

function Verify({ id, token, gwBase }: { id: string; token: string; gwBase: string }) {
  const [state, setState] = useState<"working" | "done" | "failed">("working");
  const [error, setError] = useState<string | null>(null);

  // a link works once: the page must not use it twice, as React's development double-run of effects would
  const used = useRef(false);

  useEffect(() => {
    if (used.current) return;
    used.current = true;
    signup
      .verify(gwBase, id, token)
      .then(() => setState("done"))
      .catch((err) => {
        setError(friendlyError(err));
        setState("failed");
      });
  }, [id, token, gwBase]);

  if (state === "working") return <Spinner label="Confirming your address…" />;
  if (state === "failed") {
    return (
      <>
        <PageHeader title="We could not confirm it" />
        {error ? <ErrorBanner message={error} /> : null}
        <p>
          <a href="#/signup">Apply again</a> to get a new link.
        </p>
      </>
    );
  }
  return (
    <>
      <PageHeader title="Thank you" />
      <Card>
        <p>Your address is confirmed and your application is with our team.</p>
        <p className="cm-note">We will write to you when it has been looked at. You can sign in to the DF Console once it is approved.</p>
      </Card>
    </>
  );
}
