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
import { confirms, progressLabel } from "./CloseAccount";

describe("closing the account (ADR-059)", () => {
  it("is confirmed only by the words asked for", () => {
    expect(confirms(" close my account ")).toBe(true);
    expect(confirms("close")).toBe(false);
    expect(confirms("")).toBe(false);
  });

  it("says how far it got", () => {
    expect(progressLabel({ sts: "RUNNING", step: "CONSENTS" })).toMatch(/Withdrawing consents/);
    expect(progressLabel({ sts: "STUCK", step: "IDENTITY" })).toMatch(/delayed/);
    expect(progressLabel({ sts: "DONE", step: null })).toBe("Your account is closed.");
  });
});
