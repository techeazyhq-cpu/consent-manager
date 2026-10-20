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
  webhooks,
  type WebhookDeliveryDetailDto,
  type WebhookDeliveryDto,
  type WebhookDeliverySts,
  type WebhookEndpointDto,
  type WebhookWithSecretDto,
} from "api-client";
import { Badge, Button, Card, ErrorBanner, Field, PageHeader, Spinner, friendlyError } from "ui-kit/src/index";
import type { Console } from "./console";
import { fmtDate } from "./labels";
import { managesWebhooks } from "./roles";
import { freshToken } from "./token";

/** What each message type tells, in a few words. */
export const WEBHOOK_TYPE_LABELS: Record<string, string> = {
  "consent.requested": "Consent asked for",
  "consent.notification_failed": "Couldn't notify the person",
  "consent.given": "Consent given",
  "consent.refused": "Consent refused",
  "consent.modified": "Consent narrowed or shortened",
  "consent.renewed": "Consent renewed",
  "consent.withdrawn": "Consent withdrawn",
  "consent.lapsed": "Consent lapsed",
  "receipt.issued": "Receipt issued",
  "webhook.test": "Test",
};

/** What an endpoint can subscribe to, in the order the API lists them (GET /api/cns-cmd/webhooks/types). */
export const WEBHOOK_TYPES = Object.keys(WEBHOOK_TYPE_LABELS).filter((t) => t !== "webhook.test");

/** The last outcome of a delivery, as a person reads it. */
export function lastOutcome(d: Pick<WebhookDeliveryDto, "attempts" | "lastStatus" | "lastError">): string {
  if (d.attempts === 0) return "Not tried yet";
  if (d.lastStatus != null) return `HTTP ${d.lastStatus}`;
  return d.lastError ?? "No response";
}

/** Date and time to the second: retries are minutes apart. */
const fmtTime = (iso?: string | null) =>
  iso
    ? new Date(iso).toLocaleString(undefined, {
        day: "numeric",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
      })
    : "—";

async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text);
  } catch {
    // the clipboard can be refused; the text is on screen to copy by hand
  }
}

/** The signing secret, shown this once and never again. */
function Secret({ made, onDone }: { made: WebhookWithSecretDto; onDone: () => void }) {
  return (
    <Card>
      <h3 className="cm-section-title" style={{ marginTop: 0 }}>
        Signing secret for {made.endpoint.url}: copy it now
      </h3>
      <p className="cm-note">
        This is the only time it is shown. Keep it in your secrets manager and check every message's{" "}
        <code>webhook-signature</code> with it.
        {made.endpoint.previousSecretUntil
          ? ` The old secret still signs messages too, until ${fmtDate(made.endpoint.previousSecretUntil)}, so switch over before then.`
          : ""}
      </p>
      <div className="cm-fields">
        <div>
          <span className="cm-field-label">Signing secret</span>
          <code className="df-code-inline">{made.secret}</code>{" "}
          <button className="df-link-button" onClick={() => copy(made.secret)}>
            Copy
          </button>
        </div>
      </div>
      <Button variant="primary" onClick={onDone}>
        I've stored it
      </Button>
    </Card>
  );
}

/** One delivery: its message, every attempt, and a replay when it's no longer being tried. */
function DeliveryDetail({
  detail,
  canReplay,
  busy,
  onReplay,
  onClose,
}: {
  detail: WebhookDeliveryDetailDto;
  canReplay: boolean;
  busy: boolean;
  onReplay: () => void;
  onClose: () => void;
}) {
  const d = detail.delivery;
  let body = d.payload ?? "";
  try {
    body = JSON.stringify(JSON.parse(body), null, 2);
  } catch {
    // shown as sent
  }
  return (
    <Card>
      <h3 className="cm-section-title" style={{ marginTop: 0 }}>
        {WEBHOOK_TYPE_LABELS[d.type] ?? d.type} <Badge status={d.sts} />
      </h3>
      <p className="cm-note">
        Message <code>{d.msgId}</code>, the <code>webhook-id</code> on every attempt and replay: use it to ignore repeats.
        {d.nextAttemptAt ? ` Next attempt ${fmtTime(d.nextAttemptAt)}.` : ""}
      </p>
      <pre className="df-code" style={{ maxHeight: 240, overflow: "auto" }}>
        {body}
      </pre>
      {detail.attempts.length ? (
        <table className="cm-table">
          <thead>
            <tr>
              <th>Attempt</th>
              <th>When</th>
              <th>Outcome</th>
              <th>Took</th>
            </tr>
          </thead>
          <tbody>
            {detail.attempts.map((a) => (
              <tr key={a.n}>
                <td>{a.n}</td>
                <td>{fmtTime(a.at)}</td>
                <td>{a.status != null ? `HTTP ${a.status}` : a.error}</td>
                <td>{a.millis} ms</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="cm-note">Not tried yet.</p>
      )}
      <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
        {canReplay && d.sts !== "PENDING" ? (
          <Button variant="primary" disabled={busy} onClick={onReplay}>
            Send again
          </Button>
        ) : null}
        <Button onClick={onClose}>Close</Button>
      </div>
    </Card>
  );
}

/**
 * Webhooks (ADR-047): where the organisation is told, at once, of every change to its consents. Each message is
 * signed (Standard Webhooks), retried for about two days, then kept as failed until sent again from here. Owners,
 * Administrators and Developers manage them; everyone can see what was delivered.
 */
export default function Webhooks({ console: c }: { console: Console }) {
  const manages = managesWebhooks(c.role);
  const [endpoints, setEndpoints] = useState<WebhookEndpointDto[] | null>(null);
  const [deliveries, setDeliveries] = useState<WebhookDeliveryDto[] | null>(null);
  const [url, setUrl] = useState("");
  const [description, setDescription] = useState("");
  const [chosen, setChosen] = useState<string[]>([]);
  const [made, setMade] = useState<WebhookWithSecretDto | null>(null);
  const [sts, setSts] = useState<WebhookDeliverySts | "">("");
  const [detail, setDetail] = useState<WebhookDeliveryDetailDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const token = await freshToken(c.auth);
      const [e, d] = await Promise.all([
        webhooks.list(c.gwBase, token),
        webhooks.deliveries(c.gwBase, token, { sts: sts || undefined }),
      ]);
      setEndpoints(e);
      setDeliveries(d);
    } catch (err) {
      setError(friendlyError(err));
      setEndpoints((prev) => prev ?? []);
      setDeliveries((prev) => prev ?? []);
    }
  }, [c.auth, c.gwBase, sts]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action: (token: string) => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action(await freshToken(c.auth));
      await load();
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  };

  const open = (id: string) => run(async (token) => setDetail(await webhooks.delivery(c.gwBase, token, id)));
  const urlOf = (endpointId: string) => endpoints?.find((e) => e.id === endpointId)?.url ?? "Removed endpoint";

  return (
    <>
      <PageHeader
        title="Webhooks"
        subtitle="Be told at once when consent is given, changed, withdrawn or lapses: signed, retried, and kept until delivered."
      />
      {error ? <ErrorBanner message={error} /> : null}

      {made ? <Secret made={made} onDone={() => setMade(null)} /> : null}

      {manages && !made ? (
        <Card>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              setAdding(true);
              await run(async (token) => {
                setMade(await webhooks.create(c.gwBase, token, url.trim(), description.trim(), chosen));
                setUrl("");
                setDescription("");
                setChosen([]);
              });
              setAdding(false);
            }}
          >
            <div className="cm-form-grid">
              <Field label="Endpoint URL (HTTPS, reachable from the internet)">
                <input
                  value={url}
                  maxLength={2000}
                  onChange={(e) => setUrl(e.target.value)}
                  placeholder="https://example.com/consent-webhooks"
                />
              </Field>
              <Field label="Description">
                <input
                  value={description}
                  maxLength={200}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="e.g. Orders backend"
                />
              </Field>
            </div>
            <div className="cm-field">
              <span className="cm-field-label">Messages (none ticked: every message)</span>
              <div style={{ display: "flex", flexWrap: "wrap", gap: "6px 16px" }}>
                {WEBHOOK_TYPES.map((t) => (
                  <label key={t} style={{ display: "flex", gap: 6, alignItems: "center" }}>
                    <input
                      type="checkbox"
                      checked={chosen.includes(t)}
                      onChange={(e) => setChosen(e.target.checked ? [...chosen, t] : chosen.filter((x) => x !== t))}
                    />
                    {WEBHOOK_TYPE_LABELS[t] ?? t}
                  </label>
                ))}
              </div>
            </div>
            <Button type="submit" variant="primary" disabled={busy || !url.trim()}>
              {adding ? "Adding…" : "Add endpoint"}
            </Button>
          </form>
          <p className="cm-note">
            Each message carries <code>webhook-id</code>, <code>webhook-timestamp</code> and <code>webhook-signature</code>{" "}
            (HMAC-SHA256 of id.timestamp.body), as the Standard Webhooks libraries check. Answer with any 2xx within 10
            seconds; anything else is tried again after 1 and 5 minutes, then 30 minutes, 2, 6, 12 and 24 hours.
          </p>
        </Card>
      ) : null}
      {!manages ? (
        <p className="cm-note">Owners, Administrators and Developers add and change webhooks; you can see what was delivered.</p>
      ) : null}

      <h3 className="cm-section-title">Endpoints</h3>
      {endpoints === null ? (
        <Spinner />
      ) : endpoints.length === 0 ? (
        <p className="cm-note">No endpoints yet.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>URL</th>
                <th>Messages</th>
                <th>Status</th>
                <th>Added</th>
                {manages ? <th /> : null}
              </tr>
            </thead>
            <tbody>
              {endpoints.map((e) => (
                <tr key={e.id}>
                  <td>
                    <code>{e.url}</code>
                    {e.description ? <div className="cm-note">{e.description}</div> : null}
                  </td>
                  <td>{e.types.length ? e.types.map((t) => WEBHOOK_TYPE_LABELS[t] ?? t).join(", ") : "All"}</td>
                  <td>
                    <Badge status={e.active ? "ACTIVE" : "PAUSED"} />
                    {e.previousSecretUntil ? (
                      <div className="cm-note">Old secret honoured until {fmtDate(e.previousSecretUntil)}</div>
                    ) : null}
                  </td>
                  <td>{fmtDate(e.createdAt)}</td>
                  {manages ? (
                    <td>
                      <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                        <Button disabled={busy || !e.active} onClick={() => run((t) => webhooks.test(c.gwBase, t, e.id))}>
                          Send a test
                        </Button>
                        <Button
                          disabled={busy}
                          onClick={() => {
                            if (window.confirm("Make a new signing secret? The current one keeps working for 24 hours.")) {
                              run(async (t) => setMade(await webhooks.rotateSecret(c.gwBase, t, e.id)));
                            }
                          }}
                        >
                          Rotate secret
                        </Button>
                        <Button disabled={busy} onClick={() => run((t) => webhooks.setActive(c.gwBase, t, e.id, !e.active))}>
                          {e.active ? "Pause" : "Resume"}
                        </Button>
                        <Button
                          variant="danger"
                          disabled={busy}
                          onClick={() => {
                            if (window.confirm(`Remove ${e.url}? Its deliveries are removed too.`)) {
                              run((t) => webhooks.remove(c.gwBase, t, e.id));
                            }
                          }}
                        >
                          Remove
                        </Button>
                      </div>
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h3 className="cm-section-title">Deliveries</h3>
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 8 }}>
        <label htmlFor="whk-sts">Show</label>
        <select id="whk-sts" value={sts} onChange={(e) => setSts(e.target.value as WebhookDeliverySts | "")}>
          <option value="">All</option>
          <option value="PENDING">Being tried</option>
          <option value="DELIVERED">Delivered</option>
          <option value="FAILED">Failed</option>
        </select>
        <Button disabled={busy} onClick={() => load()}>
          Refresh
        </Button>
      </div>
      {detail ? (
        <DeliveryDetail
          detail={detail}
          canReplay={manages}
          busy={busy}
          onClose={() => setDetail(null)}
          onReplay={() =>
            run(async (t) => {
              await webhooks.replay(c.gwBase, t, detail.delivery.id);
              setDetail(await webhooks.delivery(c.gwBase, t, detail.delivery.id));
            })
          }
        />
      ) : null}
      {deliveries === null ? (
        <Spinner />
      ) : deliveries.length === 0 ? (
        <p className="cm-note">Nothing delivered yet.</p>
      ) : (
        <div style={{ overflowX: "auto" }}>
          <table className="cm-table">
            <thead>
              <tr>
                <th>Message</th>
                <th>Endpoint</th>
                <th>Status</th>
                <th>Attempts</th>
                <th>Last outcome</th>
                <th>Created</th>
              </tr>
            </thead>
            <tbody>
              {deliveries.map((d) => (
                <tr key={d.id}>
                  <td>
                    <button className="df-link-button" onClick={() => open(d.id)}>
                      {WEBHOOK_TYPE_LABELS[d.type] ?? d.type}
                    </button>
                  </td>
                  <td>
                    <code>{urlOf(d.endpointId)}</code>
                  </td>
                  <td>
                    <Badge status={d.sts} />
                  </td>
                  <td>{d.attempts}</td>
                  <td>{lastOutcome(d)}</td>
                  <td>{fmtTime(d.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}
