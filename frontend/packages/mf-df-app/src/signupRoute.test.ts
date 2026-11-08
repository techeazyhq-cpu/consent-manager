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
import { applicationFrom, canApply, parseSignupRoute } from "./signupRoute";

describe("sign-up routes", () => {
  it("is not the sign-up page for the console's own hashes", () => {
    expect(parseSignupRoute("")).toBeNull();
    expect(parseSignupRoute("#/dashboard")).toBeNull();
    expect(parseSignupRoute("#state=abc&code=def")).toBeNull();
  });

  it("opens the form", () => {
    expect(parseSignupRoute("#/signup")).toEqual({ page: "form" });
  });

  it("reads a confirmation link's id and token", () => {
    expect(parseSignupRoute("#/signup/verify?id=abc-123&token=T0k_en-x")).toEqual({ page: "verify", id: "abc-123", token: "T0k_en-x" });
    expect(parseSignupRoute("#/signup/verify")).toEqual({ page: "verify", id: "", token: "" });
  });
});

describe("the application", () => {
  it("trims and leaves out what is empty", () => {
    expect(applicationFrom({ name: " Acme ", contactEmail: " a@b.example ", legalName: "", industry: " Retail ", website: " " })).toEqual({
      name: "Acme",
      contactEmail: "a@b.example",
      industry: "Retail",
    });
  });

  it("can be sent with a name and an address", () => {
    expect(canApply("Acme", "a@b.example")).toBe(true);
    expect(canApply("", "a@b.example")).toBe(false);
    expect(canApply("Acme", "nope")).toBe(false);
  });
});
