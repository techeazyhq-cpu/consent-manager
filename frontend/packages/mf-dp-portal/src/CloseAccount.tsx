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
import { CLOSE_ACCOUNT_CONFIRMATION, ERASURE_STEP_LABELS, dsr, type DpErasureDto } from "api-client";
import { Button, Card, ErrorBanner, Field, friendlyError } from "ui-kit/src/index";
import { freshToken, type Portal } from "./portal";

/** Whether what the person typed confirms closing, ignoring case and spaces at the ends. */
export const confirms = (typed: string) => typed.trim().toUpperCase() === CLOSE_ACCOUNT_CONFIRMATION;

/** How far closing the account got, in words. */
export function progressLabel(e: Pick<DpErasureDto, "sts" | "step">): string {
  if (e.sts === "DONE") return "Your account is closed.";
  if (e.sts === "STUCK") return "Closing your account is delayed. Our team has been told and will finish it.";
  return e.step ? `${ERASURE_STEP_LABELS[e.step]}…` : "Starting…";
}

/**
 * Closing the account (ADR-059): what happens and what is kept, typed confirmation, then progress until the account
 * is deleted and the person signed out. The organisations are told of each withdrawn consent by the platform; their
 * own copies of the person's data are theirs to answer for.
 */
export function CloseAccount({ portal, onClosed }: { portal: Portal; onClosed: () => void }) {
  const [typed, setTyped] = useState("");
  const [erasure, setErasure] = useState<DpErasureDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A closing already under way (another tab, or a reload) is shown, not asked for again.
  useEffect(() => {
    freshToken(portal.auth)
      .then((t) => dsr.me.erasure(portal.gwBase, t))
      .then((e) => e && e.sts !== "DONE" && setErasure(e))
      .catch(() => {});
  }, [portal.auth, portal.gwBase]);

  // Follows it until the account is gone: then the token stops working, and the person is signed out.
  useEffect(() => {
    if (!erasure || erasure.sts === "DONE") return;
    const timer = setInterval(async () => {
      try {
        const next = await dsr.me.erasure(portal.gwBase, await freshToken(portal.auth));
        if (next) setErasure(next);
        if (next?.sts === "DONE") onClosed();
      } catch {
        onClosed();
      }
    }, 2000);
    return () => clearInterval(timer);
  }, [erasure, portal.auth, portal.gwBase, onClosed]);

  if (erasure) {
    return (
      <Card className="dp-danger-zone" style={{ marginBottom: 18 }}>
        <strong>Closing your account</strong>
        <p role="status">{progressLabel(erasure)}</p>
        {erasure.sts === "DONE" ? <Button onClick={onClosed}>Sign out</Button> : null}
      </Card>
    );
  }

  return (
    <Card className="dp-danger-zone" style={{ marginBottom: 18 }}>
      <strong>Close your account</strong>
      <p>When you close your account, we:</p>
      <ul>
        <li>withdraw every consent you have given, and tell each organisation;</li>
        <li>decline the requests waiting for you, and withdraw the requests you made;</li>
        <li>erase your email address, your devices and the words of your requests;</li>
        <li>delete your account. You are signed out, and can't undo this.</li>
      </ul>
      <p className="cm-note">
        We keep the record of each consent and its receipts for seven years, as the law requires of a consent manager,
        under an id that no longer leads to you. The organisations keep their own records: ask them about the data
        they hold before you close your account. You can download your records above first.
      </p>
      {error ? <ErrorBanner message={error} /> : null}
      <form
        className="dp-form"
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          setError(null);
          try {
            setErasure(await dsr.me.closeAccount(portal.gwBase, await freshToken(portal.auth), typed));
          } catch (err) {
            setError(friendlyError(err));
          } finally {
            setBusy(false);
          }
        }}
      >
        <Field label={`Type ${CLOSE_ACCOUNT_CONFIRMATION} to confirm`}>
          <input value={typed} onChange={(e) => setTyped(e.target.value)} autoComplete="off" />
        </Field>
        <Button type="submit" variant="danger" disabled={busy || !confirms(typed)}>
          {busy ? "Closing…" : "Close my account"}
        </Button>
      </form>
    </Card>
  );
}
