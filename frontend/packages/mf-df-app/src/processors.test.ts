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
import { parseRoute } from "./routes";
import { retireWarning, useLabel } from "./Processors";

describe("processors", () => {
  it("names where a processor is used, marking a draft", () => {
    expect(useLabel({ purpose: "ORDER_UPDATES", version: 2, status: "PUBLISHED" })).toBe("ORDER_UPDATES v2");
    expect(useLabel({ purpose: "ORDER_UPDATES", version: 3, status: "DRAFT" })).toBe("ORDER_UPDATES v3 (draft)");
  });

  it("warns before retiring, and says which purposes still name it", () => {
    expect(retireWarning("Courier Ltd", [])).not.toContain("still named");
    const warning = retireWarning("Courier Ltd", [{ purpose: "ORDER_UPDATES", version: 2, status: "PUBLISHED" }]);
    expect(warning).toContain("still named by ORDER_UPDATES v2");
  });

  it("has its own page", () => {
    expect(parseRoute("#/processors")).toEqual({ page: "processors" });
  });
});
