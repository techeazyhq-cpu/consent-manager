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

import { useCallback, useEffect, useState, type ReactNode } from "react";
import { DP_REQ_STS_LABELS, dsr, friendlyError, type DpReqDto, type DpReqSts } from "api-client";
import { Button, Card, ErrorBanner, Spinner } from "./index";

type Answerer = typeof dsr.df;

/** Days left before a request is due, or how far past it, in words. */
export function dueLabel(r: Pick<DpReqDto, "dueBy" | "overdue" | "sts">, now = Date.now()): string {
  if (r.sts !== "OPEN" && r.sts !== "ACKNOWLEDGED") return "";
  const days = Math.ceil((new Date(r.dueBy).getTime() - now) / 86_400_000);
  if (r.overdue || days < 0) return `Overdue by ${Math.max(1, -days)} day${-days === 1 ? "" : "s"}`;
  if (days === 0) return "Due today";
  return `Due in ${days} day${days === 1 ? "" : "s"}`;
}

const fmt = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString() : "—");

const FILTERS: { key: DpReqSts | "ALL"; label: string }[] = [
  { key: "OPEN", label: "New" },
  { key: "ACKNOWLEDGED", label: "Being worked on" },
  { key: "RESOLVED", label: "Answered" },
  { key: "REFUSED", label: "Refused" },
  { key: "WITHDRAWN", label: "Withdrawn" },
  { key: "ALL", label: "All" },
];

/** One open request with its answer form: acknowledge, answer, or refuse citing a legal ground. */
function OpenRequest({
  req,
  answerer,
  gwBase,
  token,
  canAnswer,
  heading,
  onChanged,
}: {
  req: DpReqDto;
  answerer: Answerer;
  gwBase: string;
  token: () => Promise<string>;
  canAnswer: boolean;
  heading: ReactNode;
  onChanged: () => void;
}) {
  const [response, setResponse] = useState("");
  const [ground, setGround] = useState("");
  const [refusing, setRefusing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const act = async (fn: (t: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await fn(await token());
      onChanged();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      {error ? <ErrorBanner message={error} /> : null}
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
        <div>
          {heading}
          <div className="cm-note" style={{ margin: 0 }}>
            Received {fmt(req.receivedAt)} · <strong>{dueLabel(req)}</strong> ({fmt(req.dueBy)})
          </div>
          {req.details ? <p style={{ margin: "8px 0 0", whiteSpace: "pre-line" }}>“{req.details}”</p> : null}
        </div>
        <span style={{ alignSelf: "flex-start" }} className={`cm-badge cm-badge-${req.overdue ? "failed" : req.sts.toLowerCase()}`}>
          {DP_REQ_STS_LABELS[req.sts]}
        </span>
      </div>
      {canAnswer ? (
        <form
          style={{ display: "grid", gap: 8, marginTop: 12 }}
          onSubmit={(e) => {
            e.preventDefault();
            if (refusing) act((t) => answerer.refuse(gwBase, t, req.id, response.trim(), ground.trim()));
            else act((t) => answerer.resolve(gwBase, t, req.id, response.trim()));
          }}
        >
          <textarea
            rows={3}
            maxLength={2000}
            value={response}
            onChange={(e) => setResponse(e.target.value)}
            placeholder={refusing ? "Explain the refusal to the person" : "What was done, for the person to read"}
            aria-label="Your answer to the person"
            required
          />
          {refusing ? (
            <input
              maxLength={300}
              value={ground}
              onChange={(e) => setGround(e.target.value)}
              placeholder="The legal ground for refusing, e.g. a retention duty and the law that sets it"
              aria-label="Legal ground for refusing"
              required
            />
          ) : null}
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            {req.sts === "OPEN" && !refusing ? (
              <Button type="button" disabled={busy} onClick={() => act((t) => answerer.acknowledge(gwBase, t, req.id))}>
                Acknowledge
              </Button>
            ) : null}
            <Button type="submit" variant={refusing ? "danger" : "primary"} disabled={busy}>
              {refusing ? "Refuse" : "Send the answer"}
            </Button>
            <Button type="button" disabled={busy} onClick={() => setRefusing(!refusing)}>
              {refusing ? "Answer instead" : "Refuse…"}
            </Button>
          </div>
        </form>
      ) : null}
    </Card>
  );
}

/**
 * A queue of principal requests (ADR-058), for whoever answers them: a fiduciary its relayed requests, platform staff
 * the grievances about the platform. Open ones come first, soonest due; each is acknowledged, then answered or refused
 * with the legal ground.
 */
export function DpRequestQueue({
  answerer,
  gwBase,
  token,
  canAnswer,
  heading,
  emptyOpen,
}: {
  answerer: Answerer;
  gwBase: string;
  token: () => Promise<string>;
  canAnswer: boolean;
  /** The request's first line: what was asked and about which consent. */
  heading: (r: DpReqDto) => ReactNode;
  emptyOpen: string;
}) {
  const [filter, setFilter] = useState<DpReqSts | "ALL">("OPEN");
  const [reqs, setReqs] = useState<DpReqDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setError(null);
    try {
      setReqs(await answerer.list(gwBase, await token(), filter === "ALL" ? undefined : filter));
    } catch (err) {
      setError(friendlyError(err));
      setReqs([]);
    }
  }, [answerer, gwBase, token, filter]);

  useEffect(() => {
    setReqs(null);
    load();
  }, [load]);

  const open = (r: DpReqDto) => r.sts === "OPEN" || r.sts === "ACKNOWLEDGED";

  return (
    <>
      <div className="cm-chips" role="group" aria-label="Show requests">
        {FILTERS.map((f) => (
          <button key={f.key} aria-pressed={filter === f.key} onClick={() => setFilter(f.key)}>
            {f.label}
          </button>
        ))}
      </div>
      {error ? <ErrorBanner message={error} /> : null}
      {reqs === null ? (
        <Spinner />
      ) : reqs.length === 0 ? (
        <p className="cm-note">{filter === "OPEN" ? emptyOpen : "No requests to show."}</p>
      ) : (
        <div style={{ display: "grid", gap: 12 }}>
          {reqs.map((r) =>
            open(r) ? (
              <OpenRequest
                key={r.id}
                req={r}
                answerer={answerer}
                gwBase={gwBase}
                token={token}
                canAnswer={canAnswer}
                heading={heading(r)}
                onChanged={load}
              />
            ) : (
              <Card key={r.id}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
                  <div>
                    {heading(r)}
                    <div className="cm-note" style={{ margin: 0 }}>
                      Received {fmt(r.receivedAt)} · closed {fmt(r.closedAt)}
                      {r.answeredBy ? ` by ${r.answeredBy}` : ""}
                    </div>
                    {r.details ? <p style={{ margin: "8px 0 0", whiteSpace: "pre-line" }}>“{r.details}”</p> : null}
                    {r.response ? <p style={{ margin: "8px 0 0" }}>Answer: {r.response}</p> : null}
                    {r.refusalGround ? <p className="cm-note">Legal ground: {r.refusalGround}</p> : null}
                  </div>
                  <span style={{ alignSelf: "flex-start" }} className={`cm-badge cm-badge-${r.sts.toLowerCase()}`}>{DP_REQ_STS_LABELS[r.sts]}</span>
                </div>
              </Card>
            ),
          )}
        </div>
      )}
    </>
  );
}

