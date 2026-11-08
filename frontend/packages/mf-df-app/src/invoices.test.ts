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
import { invoiceTitle, periodLabel } from "./Billing";

describe("invoices in the DF console", () => {
  it("names a document by its kind and number", () => {
    expect(invoiceTitle({ kind: "INVOICE", number: "INV-2026-000007" })).toBe("Invoice INV-2026-000007");
    expect(invoiceTitle({ kind: "CREDIT_NOTE", number: "CN-2026-000001" })).toBe("Credit note CN-2026-000001");
  });

  it("names the period by its month in UTC, whatever the viewer's zone", () => {
    expect(periodLabel({ periodFrom: "2026-11-01T00:00:00Z" })).toMatch(/2026/);
    expect(periodLabel({ periodFrom: "2026-11-01T00:00:00Z" })).toMatch(/Nov|11/);
  });
});
