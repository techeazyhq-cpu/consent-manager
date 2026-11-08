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
import { partyLine } from "./index";

describe("partyLine", () => {
  it("says who, what it does and where, with the country by name", () => {
    expect(partyLine({ name: "Courier Ltd", role: "Delivers your orders", country: "IN" }, "en")).toBe("Courier Ltd: Delivers your orders (India)");
  });

  it("leaves out what was not said", () => {
    expect(partyLine({ name: "Courier Ltd", role: "", country: "" }, "en")).toBe("Courier Ltd");
    expect(partyLine({ name: "Courier Ltd", role: "Delivers", country: "" }, "en")).toBe("Courier Ltd: Delivers");
  });

  it("falls back to the code for a country it cannot name", () => {
    expect(partyLine({ name: "X", role: "", country: "ZZ" }, "en")).toMatch(/^X \(/);
    expect(partyLine({ name: "X", role: "", country: "not a code" }, "en")).toContain("not a code");
  });
});
