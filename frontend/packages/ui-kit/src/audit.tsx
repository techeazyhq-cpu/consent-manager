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

import type { ReactNode } from "react";

/** One audit ledger entry as `/api/aud/activity` returns it (ADR-040). */
export interface AuditRow {
  seq: number;
  evtType: string;
  occurredAt: string;
  dfId: string | null;
  cnsReqId: string | null;
  entryHash: string;
  actor: { kind: string; name: string | null };
  target?: string | null;
  details: Record<string, string>;
  erased: boolean;
}

const d = (row: AuditRow, key: string, fallback = "?") => row.details[key] ?? fallback;
const word = (code: string) => code.charAt(0) + code.slice(1).toLowerCase().replace(/_/g, " ");

/** What an entry says happened, as a sentence. Unknown types read as their name, so nothing is ever hidden. */
export function describeAudit(row: AuditRow): string {
  switch (row.evtType) {
    case "tenant.registered":
      return `Registered the tenant ${d(row, "name")} on ${d(row, "plan")}`;
    case "tenant.activated":
      return "Activated the tenant";
    case "tenant.logo-changed":
      return "Changed the logo";
    case "member.invited":
      return `Invited ${d(row, "member")} as ${word(d(row, "role"))}`;
    case "member.role-changed":
      return `Changed ${d(row, "member")} from ${word(d(row, "from"))} to ${word(d(row, "to"))}`;
    case "member.removed":
      return `Removed ${d(row, "member")} (${word(d(row, "role"))})`;
    case "subscription.started":
      return `Subscription started on ${d(row, "plan")}`;
    case "subscription.plan-changed":
      return `Changed the plan from ${d(row, "from")} to ${d(row, "to")}`;
    case "subscription.suspended":
      return "Suspended the subscription";
    case "subscription.reactivated":
      return "Reactivated the subscription";
    case "plan-change.requested":
      return `Asked to move from ${d(row, "from")} to ${d(row, "to")}`;
    case "plan-change.withdrawn":
      return `Withdrew the request for ${d(row, "to")}`;
    case "plan-change.approved":
      return `Approved the move to ${d(row, "to")}`;
    case "plan-change.declined":
      return `Declined the move to ${d(row, "to")}`;
    case "staff.operator-added":
      return `Added the operator ${d(row, "staff")}`;
    case "staff.enabled":
      return `Enabled ${d(row, "staff")}`;
    case "staff.disabled":
      return `Disabled ${d(row, "staff")}`;
    case "staff.signed-out-everywhere":
      return "Signed out of every session";
    case "batch.submitted":
      return `Sent a list of ${d(row, "rows")} (${d(row, "toSend")} to ask)${row.details.file ? `, ${row.details.file}` : ""}`;
    case "batch.finished":
      return `A list finished: ${d(row, "sent")} asked, ${d(row, "failed")} failed, ${d(row, "skipped")} skipped`;
    case "CnsReqCreatedEvt":
      return `Asked for consent${row.details.purpose ? `: ${word(row.details.purpose)}` : ""}`;
    case "CnsGrantedEvt":
      return "Gave consent";
    case "CnsDeniedEvt":
      return "Declined consent";
    case "CnsRevokedEvt":
      return "Withdrew consent";
    case "CnsExpiredEvt":
      return "Consent expired";
    case "CnsDpLinkedEvt":
      return "Linked the request to the person's account";
    case "CnsNotificationFailedEvt":
      return "Couldn't notify the person";
    case "DfActivatedEvt":
      return "Tenant activated";
    case "NtfDpDeliveryFailedEvt":
      return `A notification couldn't be delivered${row.details.channel ? ` (${word(row.details.channel)})` : ""}`;
    default:
      return row.evtType;
  }
}

/** Facts not already in the sentence, such as a note given with a decision. */
function extra(row: AuditRow): string | null {
  return row.details.note ? `“${row.details.note}”` : null;
}

const KIND_LABELS: Record<string, string> = {
  STAFF: "Platform staff",
  DF_MEMBER: "Member",
  DF_SYSTEM: "API client",
  DF: "Fiduciary",
  DP: "Person",
  SYSTEM: "Automatic",
};

const fmt = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", second: "2-digit" });

/** The audit ledger as a table: when, who, what, and (for staff) which tenant. */
export function AuditTable({ rows, tenant }: { rows: AuditRow[]; tenant?: (dfId: string | null) => ReactNode }) {
  return (
    <div style={{ overflowX: "auto" }}>
      <table className="cm-table">
        <thead>
          <tr>
            <th>When</th>
            {tenant ? <th>Tenant</th> : null}
            <th>Who</th>
            <th>What</th>
            <th title="Position in the tamper-evident ledger">#</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.seq}>
              <td style={{ whiteSpace: "nowrap" }}>{fmt(r.occurredAt)}</td>
              {tenant ? <td>{tenant(r.dfId)}</td> : null}
              <td>
                {r.actor.name ?? "—"}
                <div className="cm-note" style={{ margin: 0 }}>
                  {KIND_LABELS[r.actor.kind] ?? r.actor.kind}
                </div>
              </td>
              <td>
                {describeAudit(r)}
                {extra(r) ? (
                  <div className="cm-note" style={{ margin: 0 }}>
                    {extra(r)}
                  </div>
                ) : null}
                {r.erased ? (
                  <div className="cm-note" style={{ margin: 0 }}>
                    Details erased; the entry and its hash remain.
                  </div>
                ) : null}
              </td>
              <td title={r.entryHash}>
                <code>{r.seq}</code>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
