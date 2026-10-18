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

import { dfReg } from "api-client";
import { OrgLogo, forgetLogo as forget } from "ui-kit/src/index";
import { freshToken, type Session } from "./session";

export const forgetLogo = forget;

/** A fiduciary's logo, fetched with the signed-in staff member's token; its initials until then or if it has none. */
export default function DfLogo({
  session,
  dfId,
  name,
  size,
  version,
}: {
  session: Session;
  dfId: string;
  name: string;
  size?: number;
  version?: number;
}) {
  return (
    <OrgLogo
      id={dfId}
      name={name}
      size={size}
      version={version}
      load={async () => dfReg.logo(session.gwBase, await freshToken(session.auth), dfId)}
    />
  );
}
