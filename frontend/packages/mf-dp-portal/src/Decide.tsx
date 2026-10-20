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
import { cnsCmd, type CnsArtRdMdl, type CnsReqTermsDto } from "api-client";
import { Button, friendlyError } from "ui-kit/src/index";
import { choiceProblem, dateAfter, daysUntil, giveLabel } from "dp-kit";
import { humanise } from "./labels";
import { freshToken, type Portal } from "./portal";

/** The rules are dp-kit's, the same as the mobile app's (ADR-032 §3). */
export { choiceProblem, dateAfter, daysUntil };

/**
 * Deciding on a request (ADR-026, ADR-028): each kind of data it asks for, none ticked, with what refusing it means
 * when the organisation needs it; how long consent lasts, opening on what was asked and never past the purpose's
 * maximum. Declining is one click, as easy as giving.
 */
export default function Decide({ portal, row }: { portal: Portal; row: CnsArtRdMdl }) {
  const [terms, setTerms] = useState<CnsReqTermsDto | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const [chosen, setChosen] = useState<string[]>([]);
  const [until, setUntil] = useState("");
  const busy = portal.busy === row.cnsReqId;

  useEffect(() => {
    let live = true;
    freshToken(portal.auth)
      .then((token) => cnsCmd.terms(portal.gwBase, token, row.cnsReqId))
      .then((t) => {
        if (!live) return;
        setTerms(t);
        setUntil(dateAfter(t.requestedValidityDays));
      })
      .catch((err) => live && setFailed(friendlyError(err)));
    return () => {
      live = false;
    };
  }, [portal.auth, portal.gwBase, row.cnsReqId]);

  const decline = (
    <Button disabled={busy} onClick={() => portal.decide(row, "decline")}>
      Decline
    </Button>
  );
  if (failed) {
    return (
      <div className="dp-decide">
        <p className="cm-note">The details of this request couldn't be loaded: {failed}</p>
        <div className="dp-consent-actions">{decline}</div>
      </div>
    );
  }
  if (!terms) return <p className="cm-note">Loading what is asked…</p>;

  const days = daysUntil(until);
  const problem = choiceProblem(chosen, days, terms.maxValidityDays);
  const toggle = (code: string, on: boolean) =>
    setChosen((prev) => (on ? [...prev, code] : prev.filter((c) => c !== code)));

  return (
    <div className="dp-decide">
      <fieldset className="dp-dcats">
        <legend>Which of your data you agree to share</legend>
        {terms.dCats.map((d) => {
          const refused = d.necessary && !chosen.includes(d.code);
          return (
            <div key={d.code} className="dp-dcat">
              <label>
                <input type="checkbox" checked={chosen.includes(d.code)} onChange={(e) => toggle(d.code, e.target.checked)} />{" "}
                {humanise(d.code)}
                {d.necessary ? <span className="dp-needed"> · needed</span> : null}
              </label>
              {d.necessary && d.consequence ? (
                <p className={refused ? "dp-consequence dp-consequence-shown" : "dp-consequence"}>
                  If you don't share it: {d.consequence}
                </p>
              ) : null}
            </div>
          );
        })}
      </fieldset>

      <label className="dp-until">
        Valid until{" "}
        <input
          type="date"
          value={until}
          min={dateAfter(1)}
          max={dateAfter(terms.maxValidityDays)}
          onChange={(e) => setUntil(e.target.value)}
        />
      </label>
      <p className="cm-note">
        Asked for {terms.requestedValidityDays} days. You can choose any date up to {terms.maxValidityDays} days from
        today, and withdraw at any time.
        {terms.recipients.length ? ` They share it with: ${terms.recipients.join(", ")}.` : ""}
        {terms.retentionDays ? ` They keep it for ${terms.retentionDays} days.` : ""}
      </p>

      {problem && chosen.length > 0 ? <p className="dp-problem">{problem}</p> : null}
      <div className="dp-consent-actions">
        <Button
          variant="primary"
          disabled={busy || problem !== null}
          onClick={() => portal.decide(row, "give", { dCatCodes: chosen, validityDays: days })}
        >
          {giveLabel(chosen.length, terms.dCats.length)}
        </Button>
        {decline}
      </div>
    </div>
  );
}
