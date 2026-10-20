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
import { cnsCmd, type CnsArtRdMdl } from "api-client";
import { Button, friendlyError } from "ui-kit/src/index";
import { dateAfter, daysLeft, daysUntil, shortenedUntil } from "dp-kit";
import { fmtDate, humanise } from "./labels";
import { RENEW_PROMPT_DAYS, freshToken, type Portal } from "./portal";

export { daysLeft, shortenedUntil };

/**
 * Changing a consent in force (ADR-026 §4): stop sharing one category, end it sooner, or renew it. Renewing is the
 * only way to make it last longer, from today for up to the purpose's maximum. Withdrawing the whole consent stays on
 * the card itself, one click as giving was.
 */
export default function Manage({ portal, row }: { portal: Portal; row: CnsArtRdMdl }) {
  const left = row.validUntil ? daysLeft(row.validUntil) : null;
  const soon = left !== null && left <= RENEW_PROMPT_DAYS;
  const [maxDays, setMaxDays] = useState<number | null>(null);
  const [termsError, setTermsError] = useState<string | null>(null);
  const [sooner, setSooner] = useState("");
  const [renewTo, setRenewTo] = useState("");
  const busy = portal.busy === row.cnsArtId;
  const granted = row.grantedDCatCodes ?? row.dCatCodes;

  const loadTerms = () => {
    if (maxDays !== null) return;
    freshToken(portal.auth)
      .then((token) => cnsCmd.terms(portal.gwBase, token, row.cnsReqId))
      .then((t) => setMaxDays(t.maxValidityDays))
      .catch((err) => setTermsError(friendlyError(err)));
  };

  // a consent ending soon opens with its renewal ready
  useEffect(() => {
    if (soon) loadTerms();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [soon]);

  if (!row.validUntil) return null;
  const endDate = dateAfter(0, new Date(row.validUntil));
  // the latest a renewal may end; later than the consent ends now, or it isn't a renewal
  const renewMax = maxDays !== null ? dateAfter(maxDays) : null;
  const canRenew = renewMax !== null && renewMax > endDate;
  const renewDays = renewTo ? daysUntil(renewTo) : NaN;

  return (
    <details
      className="dp-manage"
      open={soon || undefined}
      onToggle={(e) => (e.currentTarget as HTMLDetailsElement).open && loadTerms()}
    >
      <summary>
        Change this consent
        {soon ? (
          <span className="dp-ending">
            {" "}
            · ends {left === 0 ? "today" : `in ${left} day${left === 1 ? "" : "s"}`}
          </span>
        ) : null}
      </summary>

      {granted.length > 1 ? (
        <div className="dp-manage-part">
          <strong>Stop sharing one kind of data</strong>
          <div className="dp-manage-row">
            {granted.map((c) => (
              <Button
                key={c}
                disabled={busy}
                onClick={() => {
                  if (window.confirm(`Stop sharing your ${humanise(c).toLowerCase()}? The rest of this consent stays.`)) {
                    portal.change(row, { kind: "withdraw-dcat", dCat: c });
                  }
                }}
              >
                Stop sharing {humanise(c)}
              </Button>
            ))}
          </div>
        </div>
      ) : null}

      <div className="dp-manage-part">
        <strong>End it sooner</strong>
        <div className="dp-manage-row">
          <input
            type="date"
            aria-label="End on"
            value={sooner}
            min={dateAfter(1)}
            max={dateAfter(-1, new Date(row.validUntil))}
            onChange={(e) => setSooner(e.target.value)}
          />
          <Button
            disabled={busy || !sooner || sooner >= endDate || daysUntil(sooner) < 1}
            onClick={() => portal.change(row, { kind: "shorten", until: shortenedUntil(row.validUntil!, sooner) })}
          >
            End on {sooner ? fmtDate(`${sooner}T00:00:00`) : "that date"}
          </Button>
        </div>
      </div>

      <div className="dp-manage-part">
        <strong>Renew</strong>
        {termsError ? <p className="cm-note">{termsError}</p> : null}
        {maxDays === null && !termsError ? <p className="cm-note">Loading how long it may last…</p> : null}
        {maxDays !== null && !canRenew ? (
          <p className="cm-note">
            It already lasts as long as this purpose allows ({maxDays} days). Renew nearer the end.
          </p>
        ) : null}
        {canRenew ? (
          <div className="dp-manage-row">
            <input
              type="date"
              aria-label="Renew until"
              value={renewTo}
              min={dateAfter(1, new Date(row.validUntil))}
              max={renewMax!}
              onChange={(e) => setRenewTo(e.target.value)}
            />
            <Button
              variant={soon ? "primary" : undefined}
              disabled={busy || !renewTo || renewTo <= endDate || renewTo > renewMax!}
              onClick={() => portal.change(row, { kind: "renew", days: renewDays })}
            >
              Renew until {renewTo ? fmtDate(`${renewTo}T00:00:00`) : "that date"}
            </Button>
          </div>
        ) : null}
        {canRenew ? (
          <p className="cm-note">Renewing starts a new period from today, up to {maxDays} days.</p>
        ) : null}
      </div>
    </details>
  );
}
