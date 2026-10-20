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
import { choiceProblem, dateAfter, daysUntil, giveLabel, statusOrder } from "./index";

/** The rules the web portal and the mobile app both apply (ADR-032 §3); the portal's tests cover the rest. */
describe("dp-kit", () => {
  it("won't give consent to nothing, for no time, or past the purpose's maximum", () => {
    expect(choiceProblem([], 30, 90)).toMatch(/Tick/);
    expect(choiceProblem(["EMAIL"], 0, 90)).toMatch(/after today/);
    expect(choiceProblem(["EMAIL"], 91, 90)).toMatch(/90 days/);
    expect(choiceProblem(["EMAIL"], 90, 90)).toBeNull();
  });

  it("says when consent is given for only some of the data", () => {
    expect(giveLabel(0, 3)).toBe("Give consent");
    expect(giveLabel(3, 3)).toBe("Give consent");
    expect(giveLabel(2, 3)).toBe("Give consent to 2 of 3");
  });

  it("counts calendar days to a date", () => {
    const from = new Date(2026, 9, 20, 23, 30);
    expect(dateAfter(1, from)).toBe("2026-10-21");
    expect(daysUntil("2026-11-19", from)).toBe(30);
    expect(daysUntil("not a date", from)).toBeNaN();
  });

  it("puts what waits on the person first, then what is in force", () => {
    expect([statusOrder("EXPIRED"), statusOrder("ISSUED"), statusOrder("NOTIFICATION_FAILED")]).toEqual([2, 1, 0]);
  });
});
