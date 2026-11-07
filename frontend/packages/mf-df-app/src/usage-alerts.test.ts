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
import { alertLine } from "./Billing";

describe("usage alerts (ADR-060)", () => {
  it("says what was reached and when", () => {
    expect(alertLine({ threshold: 80, used: 800, quota: 1000, raisedAt: "2026-11-08T10:00:00Z" })).toMatch(
      /^80% of the allowance used: 800 of 1,000, on /,
    );
    expect(alertLine({ threshold: 100, used: 1000, quota: 1000, raisedAt: "2026-11-09T10:00:00Z" })).toMatch(
      /^Allowance used up: 1,000 of 1,000/,
    );
  });
});
