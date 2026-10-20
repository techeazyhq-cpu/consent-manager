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
import { records, type ConsentRecordDoc, type ReceiptView, type RecordEvent } from "api-client";
import { Button, Card, ErrorBanner, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import { fmtDate, humanise } from "./labels";
import { freshToken, type Portal } from "./portal";

/** What each record event means, in the person's terms. */
export const EVENT_LABELS: Record<RecordEvent["type"], string> = {
  requested: "Asked for your consent",
  given: "You gave consent",
  refused: "You declined",
  modified: "You changed it",
  renewed: "You renewed it",
  withdrawn: "You withdrew consent",
  lapsed: "It ended: its validity ran out",
};

/** A change's detail in words: "Ended sooner", "Stopped sharing Name". */
export function changeLabel(change?: string): string | null {
  if (!change) return null;
  if (change === "SHORTENED") return "Ended sooner";
  if (change.startsWith("CATEGORY_WITHDRAWN:")) return `Stopped sharing ${humanise(change.split(":")[1])}`;
  if (change.startsWith("BACKFILLED")) return "Recorded when records began; the exact time is in the audit log";
  return change;
}

/** The JSON a compact JWS signs, decoded (base64url, UTF-8); null when it isn't a JWS. */
export function jwsPayload(jws: string): unknown {
  const part = jws.split(".")[1];
  if (!part) return null;
  try {
    const b64 = part.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(part.length / 4) * 4, "=");
    const bytes = Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    return null;
  }
}

export function saveBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

/** A receipt as a file the person keeps: the signed receipt, what it says, and where to check the signature. */
function saveReceipt(gwBase: string, r: ReceiptView) {
  const file = {
    receiptId: r.receiptId,
    forEvent: r.forEvent,
    issuedAt: r.issuedAt,
    signedReceipt: r.jws,
    verifyWith: `${gwBase}/api/cns-cmd/.well-known/receipt-jwks.json`,
    keyId: r.kid,
    sha256: r.sha256,
    says: jwsPayload(r.jws),
  };
  saveBlob(new Blob([JSON.stringify(file, null, 2)], { type: "application/json" }), `consent-receipt-${r.receiptId}.json`);
}

/**
 * One consent's history (ADR-025): every event of its ISO/IEC TS 27560 record, oldest first, each with the signed
 * receipt issued for it; what was agreed, with whom, for how long; and the whole record to download.
 */
export function History({ portal, cnsReqId }: { portal: Portal; cnsReqId: string }) {
  const [doc, setDoc] = useState<ConsentRecordDoc | null>(null);
  const [receipts, setReceipts] = useState<ReceiptView[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    freshToken(portal.auth)
      .then((token) => Promise.all([records.get(portal.gwBase, token, cnsReqId), records.receipts(portal.gwBase, token, cnsReqId)]))
      .then(([d, r]) => {
        if (!live) return;
        setDoc(d);
        setReceipts(r);
      })
      .catch((err) => live && setError(friendlyError(err)));
    return () => {
      live = false;
    };
  }, [portal.auth, portal.gwBase, cnsReqId]);

  if (error) return <ErrorBanner message={error} />;
  if (!doc) return <Spinner label="Loading the record…" />;
  const p = doc.processing;
  const purpose = p.purposes[0];
  const receiptFor = (seq: number) => receipts.find((r) => r.forEvent === seq);

  return (
    <>
      <a className="cm-back" href="#/consents">
        ← My consents
      </a>
      <PageHeader
        title={`${p.controller.name}: ${purpose?.name ?? humanise(purpose?.code ?? "")}`}
        subtitle="Everything that happened to this consent, with a signed receipt for each step."
      />

      <h3 className="cm-section-title">History</h3>
      <ol className="dp-timeline">
        {doc.events.map((e) => {
          const r = receiptFor(e.seq);
          return (
            <li key={e.seq}>
              <div className="dp-timeline-head">
                <strong>{EVENT_LABELS[e.type] ?? e.type}</strong>
                <span className="cm-note">{new Date(e.time).toLocaleString()}</span>
              </div>
              {changeLabel(e.change) ? <div>{changeLabel(e.change)}</div> : null}
              {e.personalData ? <div className="cm-note">Data: {e.personalData.map(humanise).join(", ")}</div> : null}
              {e.validUntil ? (
                <div className="cm-note">
                  Valid {fmtDate(e.validFrom)} to {fmtDate(e.validUntil)}
                </div>
              ) : null}
              {r ? (
                <Button onClick={() => saveReceipt(portal.gwBase, r)}>Download receipt</Button>
              ) : (
                <span className="cm-note">Receipt being issued…</span>
              )}
            </li>
          );
        })}
      </ol>

      <h3 className="cm-section-title">What you were asked</h3>
      <Card>
        {doc.notice.title ? <strong>{doc.notice.title}</strong> : null}
        {doc.notice.text ? <p style={{ whiteSpace: "pre-line" }}>{doc.notice.text}</p> : null}
        <dl className="cm-fields">
          <div>
            <dt>Data asked for</dt>
            <dd>{p.personalData.map((d) => humanise(d.code)).join(", ") || "—"}</dd>
          </div>
          <div>
            <dt>Who else receives it</dt>
            <dd>{p.recipients.length ? p.recipients.join(", ") : "No one"}</dd>
          </div>
          <div>
            <dt>Kept for</dt>
            <dd>{p.storageDuration ? p.storageDuration.replace(/^P(\d+)D$/, "$1 days") : "—"}</dd>
          </div>
          <div>
            <dt>Complaints about the Consent Manager</dt>
            <dd>{doc.parties.consentManager.grievanceContact}</dd>
          </div>
        </dl>
        {doc.notice.hash ? (
          <p className="cm-note" title="SHA-256 of the notice you were shown">
            Notice fingerprint <code>{doc.notice.hash.replace("sha256:", "").slice(0, 16)}…</code>
          </p>
        ) : null}
      </Card>

      <div style={{ marginTop: 18 }}>
        <Button
          onClick={() =>
            saveBlob(new Blob([JSON.stringify(doc, null, 2)], { type: "application/ld+json" }), `consent-record-${cnsReqId}.jsonld`)
          }
        >
          Download the full record
        </Button>
      </div>
    </>
  );
}

/** Every record and receipt the person has, as one file (DPDPA First Schedule B4). */
export function ExportRecords({ portal }: { portal: Portal }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Card style={{ marginBottom: 18 }}>
      <strong>Your consent records</strong>
      <p className="cm-note">
        Download every consent record and signed receipt the Consent Manager holds for you, in a standard,
        machine-readable form (ISO/IEC TS 27560).
      </p>
      {error ? <ErrorBanner message={error} /> : null}
      <Button
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          setError(null);
          try {
            saveBlob(await records.exportAll(portal.gwBase, await freshToken(portal.auth)), "my-consent-records.jsonld");
          } catch (err) {
            setError(friendlyError(err));
          } finally {
            setBusy(false);
          }
        }}
      >
        {busy ? "Preparing…" : "Download all my records"}
      </Button>
    </Card>
  );
}
