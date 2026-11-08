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

import type { NtfPreferencesDto } from "api-client";

/** What changing one switch sends: the other switch as it is. */
export function withPush(current: Pick<NtfPreferencesDto, "push" | "email">, push: boolean) {
  return { push, email: current.email };
}

export function withEmail(current: Pick<NtfPreferencesDto, "push" | "email">, email: boolean) {
  return { push: current.push, email };
}

/** What the email switch says about itself, in words. */
export function emailNote(p: Pick<NtfPreferencesDto, "email" | "emailAddress" | "canEmail">): string {
  if (!p.canEmail) return "You signed in with a mobile number, so this does not apply to you.";
  if (p.email) return `Organisations can email their requests to ${p.emailAddress}. Turn this off and they wait here for you instead.`;
  return `Organisations' request emails to ${p.emailAddress} are off. Requests wait here for you, on your dashboard.`;
}
