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
import { platformStatus, type StatusReportDto } from "api-client";
import { Badge, Card, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { headline, isStale, stateLabel, uptimeText } from "./statusText";

const fmt = (iso: string) => new Date(iso).toLocaleString();

/**
 * The platform's public status (ADR-067): each part of it, whether it is working, how often it has been over the last day and
 * month, and what staff have said about it. It needs no sign-in, since it is read when signing in is what is broken, and it
 * refreshes itself every 30 seconds.
 */
export default function StatusPage({ gwBase }: { gwBase: string }) {
  const [report, setReport] = useState<StatusReportDto | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    let alive = true;
    const load = () =>
      platformStatus
        .get(gwBase)
        .then((r) => alive && (setReport(r), setError(null), setNow(new Date())))
        .catch((err) => alive && (setError(friendlyError(err)), setNow(new Date())));
    load();
    const timer = setInterval(load, 30_000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [gwBase]);

  return (
    <div className="cm-page" style={{ maxWidth: 760, margin: "0 auto", padding: "32px 16px" }}>
      <PageHeader title="Consent Manager status" subtitle="Whether each part of the platform is working." />
      {error ? <ErrorBanner message={`We could not reach the status service: ${error}. That may itself mean the platform is down.`} /> : null}
      {!report && !error ? <Spinner /> : null}
      {report ? (
        <>
          <Card style={{ marginBottom: 16 }}>
            <h2 style={{ marginTop: 0 }}>
              <Badge status={report.overall} /> {headline(report.overall)}
            </h2>
            <p className="cm-note" style={{ marginBottom: 0 }}>
              Checked {fmt(report.updatedAt)}.
              {isStale(report.updatedAt, now) ? " This is out of date: the status service has not answered lately." : ""}
            </p>
          </Card>

          {report.notices.length > 0 ? (
            <>
              <h3 className="cm-section-title">Notices</h3>
              {report.notices.map((n) => (
                <Card key={n.id} style={{ marginBottom: 12 }}>
                  <strong>
                    <Badge status={n.severity} /> {n.title}
                  </strong>
                  {n.body ? <p style={{ whiteSpace: "pre-wrap" }}>{n.body}</p> : null}
                  <p className="cm-note" style={{ marginBottom: 0 }}>
                    {n.resolvedAt ? `Resolved ${fmt(n.resolvedAt)}. Began ${fmt(n.startedAt)}.` : `Since ${fmt(n.startedAt)}.`}
                  </p>
                </Card>
              ))}
            </>
          ) : null}

          <h3 className="cm-section-title">Systems</h3>
          <Card>
            <table className="cm-table">
              <thead>
                <tr>
                  <th>System</th>
                  <th>Now</th>
                  <th>Last 24 hours</th>
                  <th>Last 30 days</th>
                </tr>
              </thead>
              <tbody>
                {report.components.map((c) => (
                  <tr key={c.id}>
                    <td>{c.label}</td>
                    <td>
                      <Badge status={c.state} /> <span className="cm-note">{stateLabel(c.state)}</span>
                    </td>
                    <td>{uptimeText(c.uptime24h)}</td>
                    <td>{uptimeText(c.uptime30d)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>
          <p className="cm-note">
            Uptime is the share of checks, made every 30 seconds, that the system answered. A dash means it has not been watched long
            enough to say.
          </p>
        </>
      ) : null}
    </div>
  );
}
