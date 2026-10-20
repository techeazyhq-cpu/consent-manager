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

import { describe, expect, it } from "vitest";
import { claimsOf, isPrincipal, needsRefresh, personOf, tokensFrom } from "./session";

const b64url = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
const jwt = (claims: object) => `${b64url({ alg: "RS256" })}.${b64url(claims)}.sig`;

describe("the app's session", () => {
  const now = Date.parse("2026-10-20T10:00:00Z");

  it("reads who is signed in, and whether they're a principal", () => {
    const token = jwt({ sub: "dp-1", preferred_username: "asha@example.com", email: "asha@example.com", roles: ["DP"] });
    expect(personOf(token)).toEqual({ subject: "dp-1", username: "asha@example.com", email: "asha@example.com", roles: ["DP"] });
    expect(isPrincipal(personOf(token)!)).toBe(true);
    expect(isPrincipal(personOf(jwt({ sub: "u", roles: ["DF", "DF_OWNER"] }))!)).toBe(false);
    expect(personOf("not-a-jwt")).toBeNull();
    expect(claimsOf("a.!!!.c")).toBeNull();
  });

  it("reads non-ASCII names as UTF-8", () => {
    expect(personOf(jwt({ sub: "dp-2", preferred_username: "आशा" }))?.username).toBe("आशा");
  });

  it("refreshes 30 seconds before the access token expires", () => {
    const t = tokensFrom({ accessToken: jwt({ sub: "x" }), refreshToken: "r", expiresIn: 300 }, now);
    expect(t.expiresAt).toBe(now + 300_000);
    expect(needsRefresh(t, now + 269_000)).toBe(false);
    expect(needsRefresh(t, now + 271_000)).toBe(true);
  });

  it("falls back to the token's own expiry when the response gives no lifetime", () => {
    const exp = Math.floor(now / 1000) + 120;
    expect(tokensFrom({ accessToken: jwt({ sub: "x", exp }) }, now).expiresAt).toBe(exp * 1000);
  });
});
