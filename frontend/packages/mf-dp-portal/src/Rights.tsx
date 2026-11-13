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
import { DP_REQ_STS_LABELS, DP_RIGHT_LABELS, dsr, type DpReqDto, type DpRight } from "api-client";
import { Button, Card, ErrorBanner, Field, PageHeader, Spinner, dueLabel, friendlyError } from "ui-kit/src/index";
import { fmtDate, humanise } from "./labels";
import { freshToken, type Portal } from "./portal";

const RIGHTS = Object.keys(DP_RIGHT_LABELS) as DpRight[];

/** Access and erasure need nothing more than the consent; the others say what they are about. */
export const needsDetails = (right: DpRight) => right === "CORRECTION" || right === "GRIEVANCE" || right === "NOMINATION";

/**
 * Where a person goes next (DPDPA s.13(3)): once the organisation has answered, refused or let the deadline pass, to the
 * Data Protection Board; a complaint about the Consent Manager goes there once it has answered or run out of time.
 */
export function escalation(r: DpReqDto): string | null {
  const done = r.sts === "RESOLVED" || r.sts === "REFUSED" || r.overdue;
  if (!done) return null;
  return r.kind === "RELAY"
    ? "Not satisfied with the answer? Complain to the organisation's grievance officer first, then to the data protection authority."
    : "Not satisfied with the answer? You can complain to the data protection authority.";
}

function RequestCard({ portal, r, onChanged }: { portal: Portal; r: DpReqDto; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const open = r.sts === "OPEN" || r.sts === "ACKNOWLEDGED";
  const next = escalation(r);
  return (
    <Card>
      {error ? <ErrorBanner message={error} /> : null}
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <div>
          <strong>
            {r.kind === "RELAY"
              ? `${portal.orgName(r.dfId ?? "")}: ${DP_RIGHT_LABELS[r.right ?? "ACCESS"].toLowerCase()}`
              : "Complaint about the Consent Manager"}
          </strong>
          {r.prpsCode ? <div className="cm-note" style={{ margin: 0 }}>About your consent for {humanise(r.prpsCode)}</div> : null}
          <div className="cm-note" style={{ margin: 0 }}>
            Sent {fmtDate(r.receivedAt)}
            {open ? ` · answer due by ${fmtDate(r.dueBy)} (${dueLabel(r).toLowerCase()})` : ""}
          </div>
          {r.details ? <p style={{ margin: "8px 0 0", whiteSpace: "pre-line" }}>{r.details}</p> : null}
        </div>
        <span style={{ alignSelf: "flex-start" }} className={`cm-badge cm-badge-${r.overdue ? "failed" : r.sts.toLowerCase()}`}>
          {r.overdue ? "Overdue" : DP_REQ_STS_LABELS[r.sts]}
        </span>
      </div>
      {r.response ? (
        <div className="dp-answer">
          <strong>Answer{r.answeredBy ? ` from ${r.kind === "RELAY" ? portal.orgName(r.dfId ?? "") : "the Consent Manager"}` : ""}</strong>
          <p style={{ margin: "4px 0 0", whiteSpace: "pre-line" }}>{r.response}</p>
          {r.refusalGround ? <p className="cm-note">Legal ground given: {r.refusalGround}</p> : null}
        </div>
      ) : null}
      {next ? <p className="cm-note">{next}</p> : null}
      {open ? (
        <Button
          disabled={busy}
          onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              await dsr.me.withdraw(portal.gwBase, await freshToken(portal.auth), r.id);
              onChanged();
            } catch (err) {
              setError(friendlyError(err));
            } finally {
              setBusy(false);
            }
          }}
        >
          Withdraw
        </Button>
      ) : null}
    </Card>
  );
}

/** Ask an organisation to act on one of your consents; the platform passes it on and shows you the answer. */
function AskOrganisation({ portal, cnsReqId, onSent }: { portal: Portal; cnsReqId?: string; onSent: () => void }) {
  const consents = portal.rows ?? [];
  const [consent, setConsent] = useState(cnsReqId ?? "");
  const [right, setRight] = useState<DpRight>("ACCESS");
  const [details, setDetails] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (consents.length === 0) {
    return <p className="cm-note">Once an organisation asks for your consent, you can make requests about it here.</p>;
  }
  return (
    <form
      className="dp-form"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
          await dsr.me.relay(portal.gwBase, await freshToken(portal.auth), consent, right, details.trim() || undefined);
          setDetails("");
          onSent();
        } catch (err) {
          setError(friendlyError(err));
        } finally {
          setBusy(false);
        }
      }}
    >
      {error ? <ErrorBanner message={error} /> : null}
      <Field label="About">
        <select value={consent} onChange={(e) => setConsent(e.target.value)} required>
          <option value="" disabled>
            Choose a consent
          </option>
          {consents.map((r) => (
            <option key={r.cnsReqId} value={r.cnsReqId}>
              {portal.orgName(r.dfId)}: {r.ntcTitle ?? humanise(r.prpsCode)}
            </option>
          ))}
        </select>
      </Field>
      <Field label="I'd like to">
        <select value={right} onChange={(e) => setRight(e.target.value as DpRight)}>
          {RIGHTS.map((r) => (
            <option key={r} value={r}>
              {DP_RIGHT_LABELS[r]}
            </option>
          ))}
        </select>
      </Field>
      <Field label={needsDetails(right) ? "Details" : "Details (optional)"}>
        <textarea
          rows={3}
          maxLength={2000}
          value={details}
          required={needsDetails(right)}
          onChange={(e) => setDetails(e.target.value)}
          placeholder={
            right === "CORRECTION"
              ? "What is wrong, and what it should be"
              : right === "NOMINATION"
                ? "Who you nominate and how the organisation can reach them"
                : "Anything the organisation should know"
          }
        />
      </Field>
      <Button type="submit" variant="primary" disabled={busy || !consent}>
        {busy ? "Sending…" : "Send to the organisation"}
      </Button>
    </form>
  );
}

function Complain({ portal, onSent }: { portal: Portal; onSent: () => void }) {
  const [details, setDetails] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <form
      className="dp-form"
      onSubmit={async (e) => {
        e.preventDefault();
        setBusy(true);
        setError(null);
        try {
          await dsr.me.grievance(portal.gwBase, await freshToken(portal.auth), details.trim());
          setDetails("");
          onSent();
        } catch (err) {
          setError(friendlyError(err));
        } finally {
          setBusy(false);
        }
      }}
    >
      {error ? <ErrorBanner message={error} /> : null}
      <Field label="What went wrong">
        <textarea rows={3} maxLength={2000} required value={details} onChange={(e) => setDetails(e.target.value)} />
      </Field>
      <Button type="submit" disabled={busy}>
        {busy ? "Sending…" : "Send the complaint"}
      </Button>
    </form>
  );
}

/**
 * Your rights (ADR-058): ask an organisation to show, correct or erase what it holds under one of your consents, or
 * complain to it; complain about the Consent Manager itself; and follow each to its answer. The organisation answers,
 * not the Consent Manager, which only passes the request on and keeps the record.
 */
export function Rights({ portal, cnsReqId }: { portal: Portal; cnsReqId?: string }) {
  const [reqs, setReqs] = useState<DpReqDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setReqs(await dsr.me.list(portal.gwBase, await freshToken(portal.auth)));
    } catch (err) {
      setError(friendlyError(err));
      setReqs([]);
    }
  }, [portal.auth, portal.gwBase]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <>
      <PageHeader
        title="Your rights"
        subtitle="Ask an organisation about the data it holds under your consent, or complain. We pass it on and show you the answer."
      />
      {error ? <ErrorBanner message={error} /> : null}

      <h3 className="cm-section-title">Ask an organisation</h3>
      <Card style={{ marginBottom: 18 }}>
        <AskOrganisation key={cnsReqId ?? "any"} portal={portal} cnsReqId={cnsReqId} onSent={load} />
      </Card>

      <h3 className="cm-section-title">Your requests</h3>
      {reqs === null ? (
        <Spinner />
      ) : reqs.length === 0 ? (
        <p className="cm-note">You haven't made any requests.</p>
      ) : (
        <div style={{ display: "grid", gap: 12, marginBottom: 18 }}>
          {reqs.map((r) => (
            <RequestCard key={r.id} portal={portal} r={r} onChanged={load} />
          ))}
        </div>
      )}

      <h3 className="cm-section-title">Complain about the Consent Manager</h3>
      <Card>
        <p className="cm-note" style={{ marginTop: 0 }}>
          About this service itself, not an organisation: for example a request you didn't expect, or something that
          didn't work.
        </p>
        <Complain portal={portal} onSent={load} />
      </Card>
    </>
  );
}
