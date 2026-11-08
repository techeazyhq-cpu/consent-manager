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
import { headline, isStale, stateLabel, uptimeText } from "./statusText";

describe("status text", () => {
  it("headlines each overall state", () => {
    expect(headline("OPERATIONAL")).toBe("All systems operational");
    expect(headline("DEGRADED")).toContain("problems");
    expect(headline("MAJOR_OUTAGE")).toContain("down");
  });

  it("labels each component state", () => {
    expect(stateLabel("OPERATIONAL")).toBe("Operational");
    expect(stateLabel("DEGRADED")).toBe("Having problems");
    expect(stateLabel("OUTAGE")).toBe("Down");
  });

  it("shows an uptime with two decimals, or a dash when there is none to claim", () => {
    expect(uptimeText(100)).toBe("100.00%");
    expect(uptimeText(99.97)).toBe("99.97%");
    expect(uptimeText(66.666)).toBe("66.67%");
    expect(uptimeText(null)).toBe("—");
  });

  it("says when a report is old", () => {
    const now = new Date("2026-11-08T10:00:00Z");
    expect(isStale("2026-11-08T09:59:00Z", now)).toBe(false);
    expect(isStale("2026-11-08T09:56:00Z", now)).toBe(true);
  });
});
