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
import { changeNote } from "./dashboard";

describe("changeNote", () => {
  it("says nothing when no comparison is shown", () => {
    expect(changeNote(5, undefined, 30)).toBeUndefined();
  });

  it("says how a figure moved against the window before, in whole percent", () => {
    expect(changeNote(12, 10, 30)).toBe("up 20% on the 30 days before");
    expect(changeNote(7, 10, 7)).toBe("down 30% on the 7 days before");
    expect(changeNote(10, 10, 90)).toBe("same as the 90 days before");
  });

  it("does not divide by nothing", () => {
    expect(changeNote(4, 0, 30)).toBe("none in the 30 days before");
    expect(changeNote(0, 0, 30)).toBe("same as the 30 days before");
  });
});
