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

/** What a fiduciary's developers connect with: shown on the developer docs page, filled into every example. */
export interface Connection {
  gwBase: string;
  tokenUrl: string;
  clientId: string;
}

export const connectionFor = (dfId: string, gwBase: string, kcBase: string, realm: string): Connection => ({
  gwBase,
  tokenUrl: `${kcBase}/realms/${realm}/protocol/openid-connect/token`,
  clientId: `df-api-${dfId}`,
});

/** The quick start's commands, in order, ready to paste: each reads $TOKEN and the ids the step before printed. */
export function quickStart(c: Connection): { title: string; text: string; code: string }[] {
  return [
    {
      title: "1. Get a token",
      text: "With your API client's secret (Organisation page). Tokens last minutes; get a new one when it expires.",
      code: `TOKEN=$(curl -s ${c.tokenUrl} \\
  -d grant_type=client_credentials -d client_id=${c.clientId} \\
  -d client_secret="$CLIENT_SECRET" | jq -r .access_token)`,
    },
    {
      title: "2. Ask for consent",
      text: "Name the person by email or mobile number, and a purpose version you published. They are notified and decide.",
      code: `curl -s -X POST ${c.gwBase}/api/cns-cmd/cns-reqs \\
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \\
  -d '{"extDpRefTyp":"EMAIL","extDpRefVal":"asha@example.com","prpsCode":"ORDER_UPDATES","prpsVer":1}'`,
    },
    {
      title: "3. Follow it",
      text: "Read where it stands, or add a webhook to be told as soon as the person decides or changes their mind.",
      code: `curl -s ${c.gwBase}/api/cns-qry/cns-arts/$REQUEST_ID -H "Authorization: Bearer $TOKEN"`,
    },
    {
      title: "4. Check before you use the data",
      text: "Every time. Take a fresh consent token, then verify it for the purpose: a withdrawal takes effect at once.",
      code: `CNS_TKN=$(curl -s -X POST ${c.gwBase}/api/cns-cmd/cns-arts/$CONSENT_ID/token \\
  -H "Authorization: Bearer $TOKEN" | jq -r .cnsTkn)
curl -s "${c.gwBase}/api/cns-qry/cns-arts/$CONSENT_ID/verify?purpose=ORDER_UPDATES" \\
  -H "Authorization: Bearer $TOKEN" -H "X-Cns-Tkn: $CNS_TKN"`,
    },
  ];
}

/** Checking a webhook's signature in Node.js, as Standard Webhooks does (ADR-047). */
export const WEBHOOK_VERIFY_NODE = `import crypto from "node:crypto";

// rawBody: the request body exactly as received, before parsing
export function verify(secret, headers, rawBody) {
  const id = headers["webhook-id"], ts = headers["webhook-timestamp"];
  if (Math.abs(Date.now() / 1000 - Number(ts)) > 300) return false;
  const key = Buffer.from(secret.replace(/^whsec_/, ""), "base64");
  const expected = crypto.createHmac("sha256", key).update(\`\${id}.\${ts}.\`).update(rawBody).digest();
  return headers["webhook-signature"].split(" ").some((s) => {
    const given = Buffer.from(s.split(",")[1] ?? "", "base64");
    return given.length === expected.length && crypto.timingSafeEqual(given, expected);
  });
}`;
