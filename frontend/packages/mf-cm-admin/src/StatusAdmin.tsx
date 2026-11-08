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
import { platformStatus, type StatusReportDto, type StatusSeverity } from "api-client";
import { Badge, Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { freshToken, type Session } from "./session";

const fmt = (iso: string) => new Date(iso).toLocaleString();

/**
 * What the public status page says (ADR-067), and the notices staff post to it: an incident under way, planned maintenance, or
 * information. A notice stays up until it is resolved, and for a week after. Status and uptime are measured, not written.
 */
export default function StatusAdmin({ session }: { session: Session }) {
  const [report, setReport] = useState<StatusReportDto | null>(null);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [severity, setSeverity] = useState<StatusSeverity>("INCIDENT");
  const [picked, setPicked] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setReport(await platformStatus.get(session.gwBase));
    } catch (err) {
      setError(friendlyError(err));
    }
  }, [session.gwBase]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action: (token: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action(await freshToken(session.auth));
      setTitle("");
      setBody("");
      setPicked([]);
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  if (!report) return error ? <ErrorBanner message={error} /> : <Spinner />;
  return (
    <>
      <PageHeader title="Platform status" subtitle="What the public status page says, and the notices you post to it." />
      {error ? <ErrorBanner message={error} /> : null}
      <Card style={{ marginBottom: 16 }}>
        <p style={{ marginTop: 0 }}>
          <Badge status={report.overall} /> as of {fmt(report.updatedAt)}. The public page is{" "}
          <code>{window.location.origin.replace(":5176", ":5175")}/#/status</code>.
        </p>
        <ul>
          {report.components.map((c) => (
            <li key={c.id}>
              {c.label}: <Badge status={c.state} />
              {c.uptime24h === null ? "" : ` ${c.uptime24h.toFixed(2)}% in the last day`}
            </li>
          ))}
        </ul>
      </Card>

      <h3 className="cm-section-title">Post a notice</h3>
      <Card style={{ marginBottom: 16 }}>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            run((t) => platformStatus.postNotice(session.gwBase, t, { title: title.trim(), body: body.trim(), severity, componentIds: picked }));
          }}
        >
          <div className="cm-form-grid">
            <Field label="Title (shown to everyone)">
              <input value={title} maxLength={120} onChange={(e) => setTitle(e.target.value)} />
            </Field>
            <Field label="Kind">
              <select value={severity} onChange={(e) => setSeverity(e.target.value as StatusSeverity)}>
                <option value="INCIDENT">Incident: something is wrong</option>
                <option value="MAINTENANCE">Maintenance: planned work</option>
                <option value="INFO">Information</option>
              </select>
            </Field>
          </div>
          <Field label="Details (optional; plain text; never put anything personal or internal here)">
            <textarea value={body} maxLength={2000} rows={3} onChange={(e) => setBody(e.target.value)} />
          </Field>
          <fieldset style={{ border: 0, padding: 0, margin: "8px 0" }}>
            <legend className="cm-field-label">Affects (optional)</legend>
            {report.components.map((c) => (
              <label key={c.id} style={{ display: "block" }}>
                <input
                  type="checkbox"
                  checked={picked.includes(c.id)}
                  onChange={(e) => setPicked(e.target.checked ? [...picked, c.id] : picked.filter((x) => x !== c.id))}
                />{" "}
                {c.label}
              </label>
            ))}
          </fieldset>
          <Button type="submit" variant="primary" disabled={busy || !title.trim()}>
            Post it
          </Button>
        </form>
      </Card>

      <h3 className="cm-section-title">Notices</h3>
      {report.notices.length === 0 ? <p className="cm-note">None open, and none resolved in the last week.</p> : null}
      {report.notices.map((n) => (
        <Card key={n.id} style={{ marginBottom: 12 }}>
          <strong>
            <Badge status={n.severity} /> {n.title}
          </strong>
          {n.body ? <p style={{ whiteSpace: "pre-wrap" }}>{n.body}</p> : null}
          <p className="cm-note">
            Since {fmt(n.startedAt)}
            {n.resolvedAt ? `; resolved ${fmt(n.resolvedAt)}` : ""}.
          </p>
          {n.resolvedAt ? null : (
            <Button disabled={busy} onClick={() => run((t) => platformStatus.resolveNotice(session.gwBase, t, n.id))}>
              Resolve
            </Button>
          )}
        </Card>
      ))}
    </>
  );
}
