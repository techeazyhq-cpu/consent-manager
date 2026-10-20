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

import { dfReg, type CnsArtRdMdl, type CnsLcySts } from "api-client";
import { Button, OrgLogo } from "ui-kit/src/index";
import { STATUS, awaitsDecision, fmtDate, humanise } from "./labels";
import Decide from "./Decide";
import Manage from "./Manage";
import { freshToken, type Portal } from "./portal";
import { historyHref, orgHref } from "./routes";

export function DfLogo({ portal, dfId, size }: { portal: Portal; dfId: string; size?: number }) {
  return (
    <OrgLogo
      id={dfId}
      name={portal.orgName(dfId)}
      size={size}
      load={async () => dfReg.logo(portal.gwBase, await freshToken(portal.auth), dfId)}
    />
  );
}

export function StatusPill({ sts }: { sts: CnsLcySts }) {
  const { label, badge } = STATUS[sts];
  return <span className={`cm-badge cm-badge-${badge.toLowerCase()}`}>{label}</span>;
}

/**
 * One consent, as the person decides on or manages it: who asks, for what purpose, which of their data, until when,
 * and the one action that applies now. Giving and withdrawing are equally easy: one button each.
 */
export default function ConsentCard({
  portal,
  row,
  showOrg = true,
}: {
  portal: Portal;
  row: CnsArtRdMdl;
  showOrg?: boolean;
}) {
  const busy = portal.busy === (row.cnsArtId ?? row.cnsReqId);
  const org = portal.orgName(row.dfId);
  const purpose = humanise(row.prpsCode);
  // validity matters while consent is in force or being asked for, not once it has ended
  const until = row.lcySts === "ISSUED" || awaitsDecision(row.lcySts) ? fmtDate(row.validUntil) : null;

  return (
    <article className={showOrg ? "dp-consent" : "dp-consent dp-no-org"} aria-label={`${org}: ${purpose}`}>
      {showOrg ? (
        <a className="dp-consent-org" href={orgHref(row.dfId)}>
          <DfLogo portal={portal} dfId={row.dfId} size={40} />
          <span>{org}</span>
        </a>
      ) : null}
      <div className="dp-consent-body">
        <div className="dp-consent-purpose">
          <strong>{purpose}</strong>
          <StatusPill sts={row.lcySts} />
        </div>
        <div className="dp-chips" aria-label="Your data it covers">
          {row.dCatCodes.map((c) =>
            // partial consent (ADR-026): what wasn't given stays visible, marked as not shared
            row.grantedDCatCodes && !row.grantedDCatCodes.includes(c) ? (
              <span key={c} className="dp-chip dp-chip-refused" title="You didn't share this">
                {humanise(c)}
              </span>
            ) : (
              <span key={c} className="dp-chip">
                {humanise(c)}
              </span>
            ),
          )}
        </div>
        <Notice row={row} />
        <div className="cm-note">
          {awaitsDecision(row.lcySts) ? `Asked ${fmtDate(row.createdAt)}` : `Updated ${fmtDate(row.updatedAt)}`}
          {until ? ` · ${row.lcySts === "ISSUED" ? "Valid until" : "Would be valid until"} ${until}` : ""}
          {" · "}
          <a href={historyHref(row.cnsReqId)}>History and receipts</a>
        </div>
      </div>
      <div className="dp-consent-actions">
        {row.lcySts === "ISSUED" && row.cnsArtId ? (
          <Button
            variant="danger"
            disabled={busy}
            onClick={() => {
              if (
                window.confirm(
                  `Withdraw your consent to ${org} for "${purpose}"? They must stop using your data for it from now on.`,
                )
              ) {
                portal.decide(row, "withdraw");
              }
            }}
          >
            Withdraw consent
          </Button>
        ) : null}
      </div>
      {/* full width, below the card's first row: the actions above stay where they always are */}
      {awaitsDecision(row.lcySts) ? <Decide portal={portal} row={row} /> : null}
      {row.lcySts === "ISSUED" && row.cnsArtId ? <Manage portal={portal} row={row} /> : null}
    </article>
  );
}

/**
 * What the organisation told the person when it asked. Open while a decision is waiting, so it is read before
 * choosing; folded away afterwards, a click from view. Shown as text only: it is the organisation's writing.
 */
function Notice({ row }: { row: CnsArtRdMdl }) {
  if (!row.ntcTitle && !row.ntcText) {
    return awaitsDecision(row.lcySts) ? (
      <p className="cm-note">This request was sent without a notice you can read here.</p>
    ) : null;
  }
  return (
    <details className="dp-notice" open={awaitsDecision(row.lcySts)}>
      <summary>{row.ntcTitle || "The organisation's notice"}</summary>
      {row.ntcText ? <p>{row.ntcText}</p> : null}
    </details>
  );
}

export { statusOrder } from "dp-kit";
