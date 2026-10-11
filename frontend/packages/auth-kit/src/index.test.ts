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

import { describe, expect, it, vi } from "vitest";
import type Keycloak from "keycloak-js";
import { toAuthInfo } from "./index";

/** The parts of a signed-in keycloak-js instance toAuthInfo reads. */
function signedIn(tokenParsed: Record<string, unknown>): Keycloak {
  return { authenticated: true, token: "access-token", subject: "subject-1", tokenParsed, logout: vi.fn() } as
    unknown as Keycloak;
}

describe("toAuthInfo", () => {
  it("exposes the username the person signed in with", () => {
    const auth = toAuthInfo(signedIn({ preferred_username: "admin@cm.local", roles: ["CM_ADMIN"] }));

    expect(auth?.username).toBe("admin@cm.local");
  });

  it("exposes a phone number username, for accounts that have no email", () => {
    const auth = toAuthInfo(signedIn({ preferred_username: "+919876543210", roles: ["DP"] }));

    expect(auth?.username).toBe("+919876543210");
    expect(auth?.email).toBeUndefined();
  });

  it("leaves the username out when the token has none", () => {
    expect(toAuthInfo(signedIn({ roles: ["DP"] }))?.username).toBeUndefined();
  });
});

describe("toAuthInfo for a DF Console member", () => {
  it("exposes the fiduciary from the df_id claim", () => {
    expect(toAuthInfo(signedIn({ roles: ["DF", "DF_OWNER"], df_id: "df-1" }))?.dfId).toBe("df-1");
    expect(toAuthInfo(signedIn({ roles: ["DP"] }))?.dfId).toBeUndefined();
  });
});
