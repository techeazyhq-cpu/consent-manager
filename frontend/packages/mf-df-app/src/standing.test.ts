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
import { bindsOrganisation, confirmsName, standingNotice } from "./standing";

describe("standing", () => {
  it("says nothing for an active or pending organisation", () => {
    expect(standingNotice("ACTIVE")).toBeNull();
    expect(standingNotice("PENDING")).toBeNull();
    expect(standingNotice(undefined)).toBeNull();
  });

  it("explains leaving, with the day it closes", () => {
    const text = standingNotice("LEAVING", "2026-12-08T09:00:00Z")!;
    expect(text).toContain("leaving");
    expect(text).toContain("2026");
  });

  it("explains a suspension and a closing", () => {
    expect(standingNotice("SUSPENDED")).toContain("suspended");
    expect(standingNotice("CLOSED")).toContain("left");
  });

  it("only an Owner binds the organisation", () => {
    expect(bindsOrganisation("OWNER")).toBe(true);
    for (const role of ["ADMIN", "CONSENT_MANAGER", "DEVELOPER", "AUDITOR"] as const) expect(bindsOrganisation(role)).toBe(false);
  });

  it("leaving is confirmed by typing the name, in any case", () => {
    expect(confirmsName("  acme LTD ", "Acme Ltd")).toBe(true);
    expect(confirmsName("Acme", "Acme Ltd")).toBe(false);
    expect(confirmsName("", "")).toBe(false);
  });
});
