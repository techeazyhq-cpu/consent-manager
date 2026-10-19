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
  cnsBatch,
  type CnsReqBatchDto,
  type CnsReqBatchOutcome,
  type CnsReqBatchRowDto,
  type CnsReqBatchSubmitRespDto,
} from "api-client";
import { Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError, humanise } from "ui-kit/src/index";
import type { Console } from "./console";
import { fmtDate } from "./labels";
import { raisesRequests } from "./roles";
import { batchHref } from "./routes";
import { RequestPreview, TemplateFields, templateProblem, useTemplateForm } from "./Template";
import { freshToken } from "./token";

/** The largest list one batch takes, and the largest file (ADR-036). */
export const MAX_ROWS = 10_000;
export const MAX_FILE_BYTES = 5 * 1024 * 1024;

/** What happened to one person on the list, from the fiduciary's side. */
export const OUTCOME: Record<CnsReqBatchOutcome, { label: string; badge: string }> = {
  PENDING: { label: "Waiting to send", badge: "pending" },
  SENT: { label: "Sent", badge: "active" },
  INVALID: { label: "Not a valid contact", badge: "denied" },
  DUPLICATE: { label: "Listed twice", badge: "expired" },
  FAILED: { label: "Couldn't send", badge: "denied" },
};

export const BATCH_STS: Record<CnsReqBatchDto["sts"], { label: string; badge: string }> = {
  QUEUED: { label: "Queued", badge: "pending" },
  SENDING: { label: "Sending", badge: "pending" },
  DONE: { label: "Done", badge: "active" },
};

/** A list to start from: the column names the reader recognises, an email address and a mobile number. */
export const SAMPLE_CSV = "Contact,Customer ID\nasha@example.com,CUST-001\n+91 98765 43210,CUST-002\n";

/** Why a file can't be sent for checking, or null when it can. */
export function fileProblem(file: File | null): string | null {
  if (!file) return "Choose a CSV or Excel file with your list.";
  if (!/\.(csv|xlsx)$/i.test(file.name)) return "Use a CSV file or an Excel workbook (.xlsx).";
  if (file.size > MAX_FILE_BYTES) return "The file is larger than 5 MB. Split the list into smaller files.";
  return null;
}

/** Of the people to send to, the share already handled (sent or failed); 100 when there was no one to send to. */
export function progressOf(b: Pick<CnsReqBatchDto, "valid" | "sent" | "failed">): number {
  return b.valid === 0 ? 100 : Math.floor(((b.sent + b.failed) / b.valid) * 100);
}

/** Why a checked list can't go out, or null when it can. */
export function sendProblem(check: Pick<CnsReqBatchSubmitRespDto, "batch" | "available">): string | null {
  if (check.batch.valid === 0) return "No one on this list can be sent to. Fix the rows below and check again.";
  if (check.batch.valid > check.available) {
    return `This list has ${check.batch.valid} people to send to, but your plan has ${check.available} requests left this month.`;
  }
  return null;
}

const people = (n: number) => `${n.toLocaleString()} ${n === 1 ? "person" : "people"}`;

function Pill({ of }: { of: { label: string; badge: string } }) {
  return <span className={`cm-badge cm-badge-${of.badge}`}>{of.label}</span>;
}

function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function batchError(err: unknown): string {
  if (err instanceof ApiError && err.status === 402) {
    return "Your subscription isn't active, so new requests are paused. Contact the platform to reactivate it.";
  }
  if (err instanceof ApiError && err.status === 413) return "The file is too large. Split the list into smaller files.";
  return friendlyError(err);
}

function ProblemRows({ rows }: { rows: CnsReqBatchRowDto[] }) {
  return (
    <div style={{ overflowX: "auto" }}>
      <table className="cm-table">
        <thead>
          <tr>
            <th>Row</th>
            <th>Contact</th>
            <th>Your reference</th>
            <th>Outcome</th>
            <th>Why</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.rowNo}>
              <td>{r.rowNo}</td>
              <td>{r.contact || "—"}</td>
              <td>{r.ref ?? "—"}</td>
              <td>
                <Pill of={OUTCOME[r.outcome]} />
              </td>
              <td>{r.problem ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * Sending one request to everyone on a list (ADR-036): the fiduciary writes the request once, chooses a CSV or Excel
 * file, checks it (nothing is sent or counted), then sends. Rows that aren't a valid contact, or repeat one, are
 * skipped and said why.
 */
export function SendToMany({ console: c }: { console: Console }) {
  const form = useTemplateForm(c.defs);
  const [file, setFile] = useState<File | null>(null);
  const [check, setCheck] = useState<CnsReqBatchSubmitRespDto | null>(null);
  const [busy, setBusy] = useState<"check" | "send" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const t = form.template;
  // a check holds only for the file and request it was made with
  const checkedFor = JSON.stringify([t, file?.name, file?.size, file?.lastModified]);
  const [checkedKey, setCheckedKey] = useState("");
  const current = check && checkedKey === checkedFor ? check : null;

  const submit = async (dryRun: boolean) => {
    const problem = templateProblem(t) ?? fileProblem(file);
    if (problem || !file) {
      setError(problem);
      return;
    }
    setBusy(dryRun ? "check" : "send");
    setError(null);
    try {
      const res = await cnsBatch.upload(c.gwBase, await freshToken(c.auth), t, file, dryRun);
      if (dryRun) {
        setCheck(res);
        setCheckedKey(checkedFor);
      } else {
        window.location.hash = batchHref(res.batch.id);
      }
    } catch (err) {
      setError(batchError(err));
    } finally {
      setBusy(null);
    }
  };

  if (!raisesRequests(c.role)) return <p className="cm-note">Your role doesn't raise consent requests.</p>;

  const blocked = current ? sendProblem(current) : null;
  return (
    <>
      <PageHeader
        title="Send to many"
        subtitle="Ask everyone on a list for consent with one request. Each person decides for themselves."
      />
      {error ? <ErrorBanner message={error} /> : null}
      <div className="df-raise">
        <div>
          <Card>
            <h3 className="cm-section-title" style={{ marginTop: 0 }}>
              Who you're asking
            </h3>
            <Field label="Your list (CSV or Excel)">
              <input
                type="file"
                accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
            </Field>
            <p className="cm-note">
              One person per row: an email address or a mobile number with its country code (+91…), and optionally your own
              reference for them. Up to {MAX_ROWS.toLocaleString()} rows and 5 MB.{" "}
              <a
                href="#/send-to-many"
                onClick={(e) => {
                  e.preventDefault();
                  saveBlob(new Blob([SAMPLE_CSV], { type: "text/csv" }), "consent-list-sample.csv");
                }}
              >
                Download a sample list
              </a>
            </p>
            <TemplateFields console={c} form={form} />
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <Button onClick={() => submit(true)} disabled={busy !== null}>
                {busy === "check" ? "Checking…" : current ? "Check again" : "Check the list"}
              </Button>
              {current ? (
                <Button variant="primary" onClick={() => submit(false)} disabled={busy !== null || blocked !== null}>
                  {busy === "send" ? "Sending…" : `Send to ${people(current.batch.valid)}`}
                </Button>
              ) : null}
            </div>
            {!current ? <p className="cm-note">Checking reads the list and sends nothing.</p> : null}
          </Card>

          {current ? (
            <Card style={{ marginTop: 16 }}>
              <h3 className="cm-section-title" style={{ marginTop: 0 }}>
                What the check found
              </h3>
              <div className="cm-summary">
                <div className="cm-stat">
                  <b>{current.batch.total.toLocaleString()}</b>
                  <span>Rows</span>
                </div>
                <div className="cm-stat">
                  <b>{current.batch.valid.toLocaleString()}</b>
                  <span>Will be sent</span>
                </div>
                <div className="cm-stat">
                  <b>{current.batch.invalid.toLocaleString()}</b>
                  <span>Not a valid contact</span>
                </div>
                <div className="cm-stat">
                  <b>{current.batch.duplicate.toLocaleString()}</b>
                  <span>Listed twice</span>
                </div>
                <div className="cm-stat">
                  <b>{current.available.toLocaleString()}</b>
                  <span>Left this month</span>
                </div>
              </div>
              {blocked ? (
                <div className="df-callout">
                  <span>{blocked} Nothing will be sent.</span>
                </div>
              ) : null}
              {current.problems.length > 0 ? (
                <>
                  <p className="cm-note">
                    These rows will be skipped
                    {current.batch.invalid + current.batch.duplicate > current.problems.length
                      ? ` (the first ${current.problems.length} shown; the report after sending lists them all)`
                      : ""}
                    .
                  </p>
                  <ProblemRows rows={current.problems} />
                </>
              ) : (
                <p className="cm-note">Every row is a valid contact.</p>
              )}
            </Card>
          ) : null}
        </div>
        <RequestPreview console={c} form={form} />
      </div>
    </>
  );
}

/** Every list the fiduciary has sent, newest first, whether from this console or its API. */
export function Batches({ console: c }: { console: Console }) {
  const [batches, setBatches] = useState<CnsReqBatchDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    freshToken(c.auth)
      .then((token) => cnsBatch.list(c.gwBase, token))
      .then(setBatches)
      .catch((err) => {
        setError(friendlyError(err));
        setBatches([]);
      });
  }, [c.auth, c.gwBase]);

  return (
    <>
      <PageHeader title="Lists sent" subtitle="Requests sent to many people at once, from a file or through the API." />
      {raisesRequests(c.role) ? (
        <p>
          <a className="cm-btn cm-btn-primary" href="#/send-to-many" style={{ textDecoration: "none" }}>
            Send to many
          </a>
        </p>
      ) : null}
      {error ? <ErrorBanner message={error} /> : null}
      {batches === null ? (
        <Spinner />
      ) : batches.length === 0 ? (
        <p className="cm-note">No lists sent yet.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Sent</th>
                <th>Purpose</th>
                <th>From</th>
                <th>Rows</th>
                <th>Sent to</th>
                <th>Skipped</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {batches.map((b) => (
                <tr key={b.id}>
                  <td>
                    <a href={batchHref(b.id)}>{fmtDate(b.createdAt)}</a>
                  </td>
                  <td>{humanise(b.prpsCode)}</td>
                  <td>{b.fileName ?? (b.source === "API" ? "API" : b.source)}</td>
                  <td>{b.total.toLocaleString()}</td>
                  <td>{b.sent.toLocaleString()}</td>
                  <td>{(b.invalid + b.duplicate + b.failed).toLocaleString()}</td>
                  <td>
                    <Pill of={BATCH_STS[b.sts]} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

const PAGE = 100;
const OUTCOME_FILTERS: (CnsReqBatchOutcome | undefined)[] = [undefined, "SENT", "PENDING", "INVALID", "DUPLICATE", "FAILED"];

/** One list: its progress while it sends, each row's outcome, and the report to open beside the original file. */
export function BatchDetail({ console: c, id }: { console: Console; id: string }) {
  const [batch, setBatch] = useState<CnsReqBatchDto | null>(null);
  const [rows, setRows] = useState<CnsReqBatchRowDto[] | null>(null);
  const [outcome, setOutcome] = useState<CnsReqBatchOutcome | undefined>();
  const [offset, setOffset] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const token = await freshToken(c.auth);
      const [b, r] = await Promise.all([
        cnsBatch.get(c.gwBase, token, id),
        cnsBatch.rows(c.gwBase, token, id, { outcome, limit: PAGE, offset }),
      ]);
      setBatch(b);
      setRows(r);
      setError(null);
      return b;
    } catch (err) {
      setError(friendlyError(err));
      setRows((prev) => prev ?? []);
      return null;
    }
  }, [c.auth, c.gwBase, id, outcome, offset]);

  // re-read every two seconds until every row is handled
  useEffect(() => {
    let stopped = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const tick = async () => {
      const b = await load();
      if (!stopped && b && b.sts !== "DONE") timer = setTimeout(tick, 2000);
    };
    tick();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [load]);

  if (!batch) {
    return (
      <>
        <PageHeader title="List" />
        {error ? <ErrorBanner message={error} /> : <Spinner />}
      </>
    );
  }

  const progress = progressOf(batch);
  return (
    <>
      <PageHeader
        title={`${humanise(batch.prpsCode)}${batch.prpsVer ? ` v${batch.prpsVer}` : ""}: ${batch.fileName ?? "sent through the API"}`}
        subtitle={`${batch.ntcTitle} · ${batch.dCatCodes.map(humanise).join(", ")} · ${fmtDate(batch.createdAt)}`}
      />
      {error ? <ErrorBanner message={error} /> : null}
      <p>
        <a href="#/batches">← All lists</a>
      </p>
      <Card>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center" }}>
            <Pill of={BATCH_STS[batch.sts]} />
            <span className="cm-note" style={{ margin: 0 }}>
              {batch.sts === "DONE"
                ? `Finished ${fmtDate(batch.finishedAt)}`
                : `${people(batch.sent + batch.failed)} of ${batch.valid.toLocaleString()} handled`}
            </span>
          </div>
          <Button
            onClick={async () => {
              try {
                saveBlob(await cnsBatch.report(c.gwBase, await freshToken(c.auth), id), `consent-list-${id.slice(0, 8)}.csv`);
              } catch (err) {
                setError(friendlyError(err));
              }
            }}
          >
            Download the report (CSV)
          </Button>
        </div>
        <div
          className="df-progress"
          role="progressbar"
          aria-label="Sending progress"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={progress}
        >
          <div style={{ width: `${progress}%` }} />
        </div>
        <div className="cm-summary">
          <div className="cm-stat">
            <b>{batch.total.toLocaleString()}</b>
            <span>Rows</span>
          </div>
          <div className="cm-stat">
            <b>{batch.sent.toLocaleString()}</b>
            <span>Sent</span>
          </div>
          <div className="cm-stat">
            <b>{batch.pending.toLocaleString()}</b>
            <span>Waiting to send</span>
          </div>
          <div className="cm-stat">
            <b>{batch.failed.toLocaleString()}</b>
            <span>Couldn't send</span>
          </div>
          <div className="cm-stat">
            <b>{batch.invalid.toLocaleString()}</b>
            <span>Not a valid contact</span>
          </div>
          <div className="cm-stat">
            <b>{batch.duplicate.toLocaleString()}</b>
            <span>Listed twice</span>
          </div>
        </div>
        <p className="cm-note">
          Sent means the person has been asked; their decision shows under <a href="#/requests">Requests</a>.
        </p>
      </Card>

      <h3 className="cm-section-title">Each row</h3>
      <div className="df-filters" role="group" aria-label="Show rows">
        {OUTCOME_FILTERS.map((o) => (
          <button
            key={o ?? "all"}
            aria-pressed={outcome === o}
            onClick={() => {
              setOutcome(o);
              setOffset(0);
            }}
          >
            {o ? OUTCOME[o].label : "All"}
          </button>
        ))}
      </div>
      {rows === null ? (
        <Spinner />
      ) : rows.length === 0 ? (
        <p className="cm-note">No rows to show.</p>
      ) : (
        <ProblemRows rows={rows} />
      )}
      {offset > 0 || (rows?.length ?? 0) === PAGE ? (
        <div style={{ display: "flex", gap: 8, marginTop: 12, alignItems: "center" }}>
          <Button disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE))}>
            Previous
          </Button>
          <span className="cm-note" style={{ margin: 0 }}>
            Rows {offset + 1}–{offset + (rows?.length ?? 0)}
          </span>
          <Button disabled={(rows?.length ?? 0) < PAGE} onClick={() => setOffset(offset + PAGE)}>
            Next
          </Button>
        </div>
      ) : null}
    </>
  );
}
