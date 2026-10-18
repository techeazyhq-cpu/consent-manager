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
import { LOGO_MAX_BYTES, logoProblem } from "./logo";

const PNG = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0]);

describe("logo checks", () => {
  it("accepts a PNG or JPEG whose bytes match its type", () => {
    expect(logoProblem("image/png", 1000, PNG)).toBeNull();
    expect(logoProblem("image/jpeg", 1000, JPEG)).toBeNull();
  });

  it("refuses other types, including SVG, which can carry script", () => {
    expect(logoProblem("image/svg+xml", 1000, PNG)).toMatch(/PNG or JPEG/);
    expect(logoProblem("image/gif", 1000, PNG)).toMatch(/PNG or JPEG/);
  });

  it("refuses a file whose bytes aren't the type it claims", () => {
    expect(logoProblem("image/png", 1000, JPEG)).toMatch(/isn't really/);
    expect(logoProblem("image/jpeg", 1000, new TextEncoder().encode("<script>"))).toMatch(/isn't really/);
  });

  it("refuses an empty or oversized file", () => {
    expect(logoProblem("image/png", 0, PNG)).toMatch(/empty/);
    expect(logoProblem("image/png", LOGO_MAX_BYTES + 1, PNG)).toMatch(/256 KB/);
  });
});
