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
import { emailNote, withEmail, withPush } from "./prefs";

describe("notification preferences", () => {
  it("changing one switch keeps the other", () => {
    expect(withPush({ push: true, email: true }, false)).toEqual({ push: false, email: true });
    expect(withEmail({ push: false, email: false }, true)).toEqual({ push: false, email: true });
  });

  it("says where emails go, or why this does not apply", () => {
    expect(emailNote({ email: true, emailAddress: "a•••@example.com", canEmail: true })).toContain("can email their requests to a•••@example.com");
    expect(emailNote({ email: false, emailAddress: "a•••@example.com", canEmail: true })).toContain("are off");
    expect(emailNote({ email: false, emailAddress: null, canEmail: false })).toContain("mobile number");
  });

  it("says a request is never lost when emails are off", () => {
    expect(emailNote({ email: false, emailAddress: "a•••@example.com", canEmail: true })).toContain("wait here");
  });
});
