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

import { useCallback } from "react";
import { DP_RIGHT_LABELS, dsr } from "api-client";
import { PageHeader, humanise } from "ui-kit/src/index";
import { DpRequestQueue } from "ui-kit/src/requests";
import type { Console } from "./console";
import { raisesRequests } from "./roles";
import { freshToken } from "./token";

/**
 * Requests from data principals (ADR-058), relayed by the Consent Manager about one of their consents with you: to see,
 * correct or erase their data, a complaint, or a nomination. The consent's id tells you who is asking, as your own
 * records know them; the Consent Manager sends no contact details. Answer by the date shown. Owners, Administrators and
 * Consent managers answer; everyone sees them. Your webhooks get principal_request.received as each arrives.
 */
export default function PrincipalRequests({ console: c }: { console: Console }) {
  const token = useCallback(() => freshToken(c.auth), [c.auth]);
  return (
    <>
      <PageHeader
        title="Principal requests"
        subtitle="People asking about the data you hold under their consent. Answer each by its due date; a refusal names its legal ground."
      />
      <DpRequestQueue
        answerer={dsr.df}
        gwBase={c.gwBase}
        token={token}
        canAnswer={raisesRequests(c.role)}
        emptyOpen="No new requests."
        heading={(r) => (
          <>
            <strong>{r.right ? DP_RIGHT_LABELS[r.right] : "Request"}</strong>
            <div className="cm-note" style={{ margin: 0 }}>
              {r.prpsCode ? `${humanise(r.prpsCode)} · ` : ""}consent request <code>{r.cnsReqId}</code>
            </div>
          </>
        )}
      />
      {!raisesRequests(c.role) ? (
        <p className="cm-note">An Owner, Administrator or Consent manager answers requests.</p>
      ) : null}
    </>
  );
}
