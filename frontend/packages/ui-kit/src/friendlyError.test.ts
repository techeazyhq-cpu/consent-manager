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
import { ApiError, friendlyError, problemCode } from "api-client";

describe("friendlyError", () => {
  it("prefers a problem's explanation to its title", () => {
    const err = { problem: { title: "An account with that email already exists", detail: "An account with a@b.example already exists" } };
    expect(friendlyError(err)).toBe("An account with a@b.example already exists");
    expect(friendlyError({ problem: { title: "An account with that email already exists" } })).toBe(
      "An account with that email already exists",
    );
    expect(friendlyError(new Error("offline"))).toBe("offline");
  });

  it("gives a failure on the platform's side a reference to quote", () => {
    const server = { problem: { code: "internal-error", title: "Something went wrong on our side", status: 500, correlationId: "c-1" } };
    expect(friendlyError(server)).toBe("Something went wrong on our side (reference c-1)");
    // a mistake by the caller has nothing to report
    const caller = { problem: { code: "invalid-count", title: "Count out of range", status: 400, correlationId: "c-2" } };
    expect(friendlyError(caller)).toBe("Count out of range");
  });
});

describe("the error code", () => {
  it("is the problem's code, else the end of its type, never its title", () => {
    expect(problemCode({ code: "quota-exceeded", type: "urn:cm:problem:quota-exceeded" })).toBe("quota-exceeded");
    expect(problemCode({ type: "urn:cm:problem:request-replayed" })).toBe("request-replayed");
    expect(problemCode({ type: "about:blank" })).toBeUndefined();
    expect(problemCode(null)).toBeUndefined();
    expect(new ApiError(429, { type: "urn:cm:problem:quota-exceeded", title: "Monthly allowance used up" }).code).toBe("quota-exceeded");
    expect(new ApiError(500, null).code).toBeUndefined();
  });
});
