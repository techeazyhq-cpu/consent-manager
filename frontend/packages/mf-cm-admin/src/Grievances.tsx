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
import { dsr } from "api-client";
import { PageHeader } from "ui-kit/src/index";
import { DpRequestQueue } from "ui-kit/src/requests";
import { freshToken, type Session } from "./session";

/**
 * Grievances about the Consent Manager itself (ADR-058), from data principals: answered by platform staff, by the date
 * fixed when each arrived, and a refusal names its legal ground. Requests about a fiduciary's data go to that fiduciary,
 * not here.
 */
export default function Grievances({ session }: { session: Session }) {
  const token = useCallback(() => freshToken(session.auth), [session.auth]);
  return (
    <>
      <PageHeader
        title="Grievances"
        subtitle="Complaints from data principals about the Consent Manager. Answer each by its due date."
      />
      <DpRequestQueue
        answerer={dsr.grievances}
        gwBase={session.gwBase}
        token={token}
        canAnswer
        emptyOpen="No new grievances."
        heading={(r) => (
          <strong>
            Grievance <code>{r.id.slice(0, 8)}</code>
          </strong>
        )}
      />
    </>
  );
}
