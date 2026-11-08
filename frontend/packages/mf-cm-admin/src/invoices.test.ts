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
import { formatMinor } from "api-client";
import { lastMonth, parsePrice } from "./Billing";

describe("invoices in the admin console", () => {
  it("invoices the month that has just ended, across a year end", () => {
    expect(lastMonth(new Date("2026-11-08T10:00:00Z"))).toBe("2026-10");
    expect(lastMonth(new Date("2027-01-01T00:00:00Z"))).toBe("2026-12");
  });

  it("reads a price typed in major units as minor units, and refuses anything else", () => {
    expect(parsePrice("2999")).toBe(299900);
    expect(parsePrice("2999.5")).toBe(299950);
    expect(parsePrice(" 0.05 ")).toBe(5);
    expect(parsePrice("")).toBeNull();
    expect(parsePrice("-1")).toBeNull();
    expect(parsePrice("1.234")).toBeNull();
    expect(parsePrice("1,000")).toBeNull();
    expect(parsePrice("free")).toBeNull();
  });

  it("shows money with its currency and two decimals", () => {
    expect(formatMinor(1250, "USD", "en-US")).toBe("$12.50");
    expect(formatMinor(-50, "USD", "en-US")).toBe("-$0.50");
    expect(formatMinor(1250, "NOT-A-CURRENCY", "en-US")).toBe("12.50 NOT-A-CURRENCY");
  });
});
